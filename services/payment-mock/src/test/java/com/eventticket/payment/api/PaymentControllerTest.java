package com.eventticket.payment.api;

import com.eventticket.payment.api.dto.CreatePaymentRequest;
import com.eventticket.payment.api.dto.PaymentResponse;
import com.eventticket.payment.api.error.ApiException;
import com.eventticket.payment.domain.PaymentStatus;
import com.eventticket.payment.service.PaymentService;
import com.eventticket.payment.service.PaymentService.PaymentResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    private static final String VALID_BODY = """
            {"booking_id": "booking-123", "amount": 1500.00, "currency": "INR"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void newPaymentReturns201() throws Exception {
        when(paymentService.createPayment(eq("key-1"), any(CreatePaymentRequest.class)))
                .thenReturn(new PaymentResult(response(PaymentStatus.SUCCEEDED), false));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "key-1")
                        .header("X-Correlation-Id", "corr-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(header().string("X-Correlation-Id", "corr-abc"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.booking_id").value("booking-123"));
    }

    @Test
    void replayReturns200WithReplayHeader() throws Exception {
        when(paymentService.createPayment(eq("key-1"), any(CreatePaymentRequest.class)))
                .thenReturn(new PaymentResult(response(PaymentStatus.SUCCEEDED), true));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"));
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"))
                .andExpect(jsonPath("$.correlation_id").exists());
        verifyNoInteractions(paymentService);
    }

    @Test
    void invalidBodyReturns400WithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"booking_id": "", "amount": -5, "currency": "rupees"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(3));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void conflictUsesCommonErrorFormat() throws Exception {
        when(paymentService.createPayment(eq("key-1"), any(CreatePaymentRequest.class)))
                .thenThrow(ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "different payload"));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments"));
    }

    @Test
    void invalidPaymentIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/payments/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    private static PaymentResponse response(PaymentStatus status) {
        return new PaymentResponse(UUID.randomUUID(), "booking-123", new BigDecimal("1500.00"), "INR",
                status, null, Instant.now());
    }
}
