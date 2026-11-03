package com.eventticket.payment.service;

import com.eventticket.payment.config.PaymentProperties;
import com.eventticket.payment.domain.Payment;
import com.eventticket.payment.domain.PaymentMode;
import com.eventticket.payment.domain.PaymentOutcome;
import com.eventticket.payment.domain.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSimulatorTest {

    private final Payment payment = Payment.processing("k", "f", "b-1", new BigDecimal("10.00"), "INR");

    @Test
    void successModeSucceeds() {
        PaymentSimulator simulator = new PaymentSimulator(new PaymentProperties(PaymentMode.SUCCESS, 0, 0));

        assertThat(simulator.charge(payment).status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void failModeDeclines() {
        PaymentSimulator simulator = new PaymentSimulator(new PaymentProperties(PaymentMode.FAIL, 0, 0));

        PaymentOutcome outcome = simulator.charge(payment);

        assertThat(outcome.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outcome.failureReason()).startsWith("CARD_DECLINED");
    }

    @Test
    void slowModeDelaysThenSucceeds() {
        PaymentSimulator simulator = new PaymentSimulator(new PaymentProperties(PaymentMode.SLOW, 200, 0));

        long start = System.currentTimeMillis();
        PaymentOutcome outcome = simulator.charge(payment);

        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(190);
        assertThat(outcome.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void modeCanBeSwitchedAtRuntime() {
        PaymentSimulator simulator = new PaymentSimulator(new PaymentProperties(PaymentMode.SUCCESS, 0, 0));

        simulator.update(PaymentMode.FAIL, null);

        assertThat(simulator.getMode()).isEqualTo(PaymentMode.FAIL);
        assertThat(simulator.charge(payment).status()).isEqualTo(PaymentStatus.FAILED);
    }
}
