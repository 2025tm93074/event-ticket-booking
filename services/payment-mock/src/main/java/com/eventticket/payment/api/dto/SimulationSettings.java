package com.eventticket.payment.api.dto;

import com.eventticket.payment.domain.PaymentMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Runtime switch for the simulated gateway, so failures can be injected during a demo without a restart. */
public record SimulationSettings(
        @NotNull PaymentMode mode,
        @PositiveOrZero @Max(60_000) Long delayMs) {
}
