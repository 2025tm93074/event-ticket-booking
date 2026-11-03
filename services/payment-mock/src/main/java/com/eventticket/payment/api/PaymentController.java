package com.eventticket.payment.api;

import com.eventticket.payment.api.dto.CreatePaymentRequest;
import com.eventticket.payment.api.dto.PaymentResponse;
import com.eventticket.payment.api.error.ApiError;
import com.eventticket.payment.api.error.ApiException;
import com.eventticket.payment.service.PaymentService;
import com.eventticket.payment.service.PaymentService.PaymentResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REPLAY_HEADER = "Idempotent-Replay";
    private static final int MAX_KEY_LENGTH = 100;

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Charge a booking",
            description = "Creates a payment attempt, or replays the stored result for a repeated Idempotency-Key. "
                    + "A declined charge is still 201 with status FAILED; errors use the common error format.")
    @Parameter(name = IDEMPOTENCY_KEY_HEADER, in = ParameterIn.HEADER, required = true,
            description = "Unique key per logical payment, max 100 characters")
    @ApiResponse(responseCode = "201", description = "Payment attempt recorded (status SUCCEEDED or FAILED)")
    @ApiResponse(responseCode = "200", description = "Replay of an earlier request with the same key and payload")
    @ApiResponse(responseCode = "400", description = "Validation failed or Idempotency-Key missing",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Key reused with a different payload, booking already paid, "
            + "or original request still processing", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<PaymentResponse> createPayment(
            @Parameter(hidden = true) @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        String key = validateKey(idempotencyKey);
        PaymentResult result = paymentService.createPayment(key, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header(REPLAY_HEADER, String.valueOf(result.replayed()))
                .body(result.response());
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get a payment by id")
    @ApiResponse(responseCode = "200", description = "Payment found")
    @ApiResponse(responseCode = "404", description = "Payment not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PaymentResponse getPayment(@PathVariable UUID paymentId) {
        return paymentService.getPayment(paymentId);
    }

    private static String validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("MISSING_IDEMPOTENCY_KEY", "Header Idempotency-Key is required");
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_KEY_LENGTH) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
        return key;
    }
}
