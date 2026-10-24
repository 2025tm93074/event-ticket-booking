package com.eventticket.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private Payment newPayment() {
        return Payment.processing("k", "f", "b-1", new BigDecimal("10.00"), "INR");
    }

    @Test
    void startsInProcessing() {
        assertThat(newPayment().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void processingCanMoveToSucceededOrFailed() {
        Payment ok = newPayment();
        ok.complete(PaymentOutcome.succeeded());
        assertThat(ok.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        Payment declined = newPayment();
        declined.complete(PaymentOutcome.failed("CARD_DECLINED"));
        assertThat(declined.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(declined.getFailureReason()).isEqualTo("CARD_DECLINED");
    }

    @Test
    void terminalStateCannotChange() {
        Payment payment = newPayment();
        payment.complete(PaymentOutcome.failed("CARD_DECLINED"));

        assertThatThrownBy(() -> payment.complete(PaymentOutcome.succeeded()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void outcomeMustBeTerminal() {
        assertThatThrownBy(() -> newPayment().complete(new PaymentOutcome(PaymentStatus.PROCESSING, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
