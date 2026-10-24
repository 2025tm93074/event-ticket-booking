package com.eventticket.payment.domain;

/** Result returned by the simulated gateway for one charge. */
public record PaymentOutcome(PaymentStatus status, String failureReason) {

    public static PaymentOutcome succeeded() {
        return new PaymentOutcome(PaymentStatus.SUCCEEDED, null);
    }

    public static PaymentOutcome failed(String reason) {
        return new PaymentOutcome(PaymentStatus.FAILED, reason);
    }
}
