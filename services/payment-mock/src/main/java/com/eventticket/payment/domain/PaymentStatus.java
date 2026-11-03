package com.eventticket.payment.domain;

/**
 * Payment state machine: PROCESSING -> SUCCEEDED or PROCESSING -> FAILED.
 * SUCCEEDED and FAILED are terminal.
 */
public enum PaymentStatus {
    PROCESSING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this != PROCESSING;
    }
}
