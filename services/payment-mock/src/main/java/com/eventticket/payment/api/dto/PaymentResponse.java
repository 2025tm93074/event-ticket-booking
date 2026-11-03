package com.eventticket.payment.api.dto;

import com.eventticket.payment.domain.Payment;
import com.eventticket.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        String bookingId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String failureReason,
        Instant createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getBookingId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getFailureReason(),
                payment.getCreatedAt());
    }
}
