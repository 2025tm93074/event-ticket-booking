package com.eventticket.payment.service;

import com.eventticket.payment.api.dto.CreatePaymentRequest;
import com.eventticket.payment.api.dto.PaymentResponse;
import com.eventticket.payment.api.error.ApiException;
import com.eventticket.payment.config.PaymentProperties;
import com.eventticket.payment.domain.Payment;
import com.eventticket.payment.domain.PaymentOutcome;
import com.eventticket.payment.domain.PaymentRepository;
import com.eventticket.payment.domain.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Business rules owned by the Payment service:
 * <ul>
 *   <li>BR4 (payment side): a declined charge is recorded as FAILED and reported as such, never as success.</li>
 *   <li>BR7: one Idempotency-Key produces at most one charge; repeats get the stored response.</li>
 *   <li>BR8: reusing an Idempotency-Key with a different payload is rejected with 409.</li>
 *   <li>BR9: a booking has at most one in-flight or successful payment (partial unique index).</li>
 * </ul>
 *
 * The key is claimed by inserting a PROCESSING row before the gateway is called. The unique
 * constraint on idempotency_key makes the database decide which of two simultaneous requests wins;
 * the loser reads the winner's row and replays its result. A plain "select, then insert if absent"
 * in code would let both requests through.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final long POLL_INTERVAL_MS = 100;

    private final PaymentRepository repository;
    private final PaymentSimulator simulator;
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository repository, PaymentSimulator simulator,
                          PaymentProperties properties, ObjectMapper objectMapper) {
        this.repository = repository;
        this.simulator = simulator;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public PaymentResult createPayment(String idempotencyKey, CreatePaymentRequest request) {
        String fingerprint = RequestFingerprint.of(request);

        Optional<Payment> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }

        Payment payment;
        try {
            payment = repository.saveAndFlush(Payment.processing(
                    idempotencyKey, fingerprint, request.bookingId().trim(), request.amount(), request.currency()));
        } catch (DataIntegrityViolationException e) {
            // Lost the race: either the same key was claimed a moment ago (replay it),
            // or this booking already has an active payment under a different key (BR9).
            return repository.findByIdempotencyKey(idempotencyKey)
                    .map(winner -> {
                        log.info("concurrent duplicate detected", kv("idempotency_key", idempotencyKey));
                        return replay(winner, fingerprint);
                    })
                    .orElseThrow(() -> ApiException.conflict("BOOKING_ALREADY_PAID",
                            "Booking " + request.bookingId() + " already has a payment in progress or completed"));
        }

        log.info("payment attempt created",
                kv("payment_id", payment.getId()), kv("idempotency_key", idempotencyKey));

        PaymentOutcome outcome = simulator.charge(payment);
        payment.complete(outcome);
        PaymentResponse response = PaymentResponse.from(payment);
        payment.storeResponse(toJson(response));
        repository.save(payment);

        log.info("payment completed",
                kv("payment_id", payment.getId()), kv("status", payment.getStatus()),
                kv("failure_reason", payment.getFailureReason()));
        return new PaymentResult(response, false);
    }

    public PaymentResponse getPayment(UUID paymentId) {
        return repository.findById(paymentId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "Payment " + paymentId + " not found"));
    }

    private PaymentResult replay(Payment payment, String fingerprint) {
        if (!payment.matchesFingerprint(fingerprint)) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with a different request payload");
        }
        Payment completed = awaitCompletion(payment);
        log.info("idempotent replay",
                kv("payment_id", completed.getId()),
                kv("idempotency_key", completed.getIdempotencyKey()),
                kv("status", completed.getStatus()));
        return new PaymentResult(fromJson(completed.getResponseBody()), true);
    }

    /** A duplicate that arrives while the original is still being charged waits for its result. */
    private Payment awaitCompletion(Payment payment) {
        long deadline = System.currentTimeMillis() + properties.inProgressWaitMs();
        Payment current = payment;
        while (current.getStatus() == PaymentStatus.PROCESSING || current.getResponseBody() == null) {
            if (System.currentTimeMillis() >= deadline) {
                throw ApiException.conflict("PAYMENT_IN_PROGRESS",
                        "A payment with this Idempotency-Key is still processing; retry later");
            }
            sleep();
            current = repository.findById(current.getId()).orElseThrow();
        }
        return current;
    }

    private String toJson(PaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise payment response", e);
        }
    }

    private PaymentResponse fromJson(String body) {
        try {
            return objectMapper.readValue(body, PaymentResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored payment response is unreadable", e);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for payment", e);
        }
    }

    public record PaymentResult(PaymentResponse response, boolean replayed) {
    }
}
