package com.eventticket.payment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        @Schema(description = "Booking being paid for (owned by the Booking service)", example = "b7c1e7a4-3f0e-4c47-9d0b-6f1f7f0f2a11")
        @NotBlank @Size(max = 64)
        String bookingId,

        @Schema(description = "Amount to charge", example = "1500.00")
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal amount,

        @Schema(description = "ISO 4217 currency code", example = "INR")
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter uppercase currency code")
        String currency) {
}
