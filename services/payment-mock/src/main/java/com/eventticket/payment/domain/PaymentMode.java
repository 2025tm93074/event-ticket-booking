package com.eventticket.payment.domain;

/** How the simulated payment gateway behaves. */
public enum PaymentMode {
    /** Every charge succeeds immediately. */
    SUCCESS,
    /** Every charge is declined. Used to trigger the booking saga's compensation path. */
    FAIL,
    /** Every charge succeeds after a configurable delay. Used to exercise caller timeouts. */
    SLOW
}
