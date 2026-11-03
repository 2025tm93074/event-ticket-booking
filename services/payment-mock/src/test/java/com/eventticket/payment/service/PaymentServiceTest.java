package com.eventticket.payment.service;

import com.eventticket.payment.api.dto.CreatePaymentRequest;
import com.eventticket.payment.api.dto.PaymentResponse;
import com.eventticket.payment.api.error.ApiException;
import com.eventticket.payment.config.PaymentProperties;
import com.eventticket.payment.domain.Payment;
import com.eventticket.payment.domain.PaymentMode;
import com.eventticket.payment.domain.PaymentOutcome;
import com.eventticket.payment.domain.PaymentRepository;
import com.eventticket.payment.domain.PaymentStatus;
import com.eventticket.payment.service.PaymentService.PaymentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String KEY = "pay-booking-123";
    private static final CreatePaymentRequest REQUEST =
            new CreatePaymentRequest("booking-123", new BigDecimal("1500.00"), "INR");

    @Mock
    private PaymentRepository repository;
    @Mock
    private PaymentSimulator simulator;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private PaymentService service;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(PaymentMode.SUCCESS, 0, 500);
        service = new PaymentService(repository, simulator, properties, objectMapper);
    }

    @Test
    @DisplayName("New key: charges exactly once, stores the response and returns it as not replayed")
    void newKeyChargesOnce() {
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(simulator.charge(any(Payment.class))).thenReturn(PaymentOutcome.succeeded());

        PaymentResult result = service.createPayment(KEY, REQUEST);

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(simulator, times(1)).charge(any(Payment.class));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(saved.getValue().getResponseBody()).contains("\"payment_id\"");
    }

    @Test
    @DisplayName("BR4: a declined charge is recorded and returned as FAILED, never SUCCEEDED")
    void declinedChargeIsFailed() {
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(simulator.charge(any(Payment.class))).thenReturn(PaymentOutcome.failed("CARD_DECLINED"));

        PaymentResult result = service.createPayment(KEY, REQUEST);

        assertThat(result.response().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.response().failureReason()).isEqualTo("CARD_DECLINED");
    }

    @Test
    @DisplayName("BR7: same key and same payload replays the stored response without charging again")
    void sameKeySamePayloadReplays() {
        Payment stored = completedPayment(REQUEST, PaymentOutcome.succeeded());
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(stored));

        PaymentResult result = service.createPayment(KEY, REQUEST);

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().paymentId()).isEqualTo(stored.getId());
        verify(simulator, never()).charge(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("BR7: equivalent amounts (1500 vs 1500.00) count as the same payload")
    void equivalentAmountReplays() {
        Payment stored = completedPayment(REQUEST, PaymentOutcome.succeeded());
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(stored));

        CreatePaymentRequest sameValue = new CreatePaymentRequest("booking-123", new BigDecimal("1500"), "INR");

        assertThat(service.createPayment(KEY, sameValue).replayed()).isTrue();
    }

    @Test
    @DisplayName("BR8: same key with a different payload is rejected with 409")
    void sameKeyDifferentPayloadRejected() {
        Payment stored = completedPayment(REQUEST, PaymentOutcome.succeeded());
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(stored));

        CreatePaymentRequest different = new CreatePaymentRequest("booking-123", new BigDecimal("999.00"), "INR");

        assertThatThrownBy(() -> service.createPayment(KEY, different))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                });
        verify(simulator, never()).charge(any());
    }

    @Test
    @DisplayName("BR7: simultaneous duplicate loses the insert race and replays the winner's result")
    void simultaneousDuplicateReplaysWinner() {
        Payment winner = completedPayment(REQUEST, PaymentOutcome.succeeded());
        when(repository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty())       // both requests saw no row
                .thenReturn(Optional.of(winner));   // after losing the unique-constraint race
        when(repository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("uq_payments_idempotency_key"));

        PaymentResult result = service.createPayment(KEY, REQUEST);

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().paymentId()).isEqualTo(winner.getId());
        verify(simulator, never()).charge(any());
    }

    @Test
    @DisplayName("BR7: duplicate arriving while the original is PROCESSING waits and replays the final result")
    void duplicateWaitsForInFlightPayment() {
        Payment inFlight = withId(Payment.processing(KEY, RequestFingerprint.of(REQUEST),
                REQUEST.bookingId(), REQUEST.amount(), REQUEST.currency()));
        Payment finished = completedPayment(REQUEST, PaymentOutcome.succeeded());
        ReflectionTestUtils.setField(finished, "id", inFlight.getId());

        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(inFlight));
        when(repository.findById(inFlight.getId())).thenReturn(Optional.of(finished));

        PaymentResult result = service.createPayment(KEY, REQUEST);

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(simulator, never()).charge(any());
    }

    @Test
    @DisplayName("Duplicate gives up with 409 PAYMENT_IN_PROGRESS if the original never finishes")
    void duplicateTimesOutWhileProcessing() {
        Payment inFlight = withId(Payment.processing(KEY, RequestFingerprint.of(REQUEST),
                REQUEST.bookingId(), REQUEST.amount(), REQUEST.currency()));
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(inFlight));
        when(repository.findById(inFlight.getId())).thenReturn(Optional.of(inFlight));

        assertThatThrownBy(() -> service.createPayment(KEY, REQUEST))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PAYMENT_IN_PROGRESS"));
    }

    @Test
    @DisplayName("BR9: a second key for a booking that already has an active payment is rejected")
    void secondKeyForPaidBookingRejected() {
        when(repository.findByIdempotencyKey("another-key")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("uq_payments_booking_active"));

        assertThatThrownBy(() -> service.createPayment("another-key", REQUEST))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("BOOKING_ALREADY_PAID");
                });
        verify(simulator, never()).charge(any());
    }

    @Test
    @DisplayName("Unknown payment id returns 404")
    void unknownPaymentNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayment(id))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private Payment completedPayment(CreatePaymentRequest request, PaymentOutcome outcome) {
        Payment payment = withId(Payment.processing(KEY, RequestFingerprint.of(request),
                request.bookingId(), request.amount(), request.currency()));
        payment.complete(outcome);
        try {
            payment.storeResponse(objectMapper.writeValueAsString(PaymentResponse.from(payment)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return payment;
    }

    private static Payment withId(Payment payment) {
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }
}
