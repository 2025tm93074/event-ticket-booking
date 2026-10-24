package com.eventticket.payment.service;

import com.eventticket.payment.config.PaymentProperties;
import com.eventticket.payment.domain.Payment;
import com.eventticket.payment.domain.PaymentMode;
import com.eventticket.payment.domain.PaymentOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Stands in for an external payment gateway. Behaviour is controlled by PAYMENT_MODE at startup
 * and can be switched at runtime through the admin endpoint.
 */
@Component
public class PaymentSimulator {

    static final String DECLINE_REASON = "CARD_DECLINED: simulated decline (mode=FAIL)";

    private static final Logger log = LoggerFactory.getLogger(PaymentSimulator.class);

    private final AtomicReference<PaymentMode> mode;
    private final AtomicLong delayMs;

    public PaymentSimulator(PaymentProperties properties) {
        this.mode = new AtomicReference<>(properties.mode() == null ? PaymentMode.SUCCESS : properties.mode());
        this.delayMs = new AtomicLong(properties.delayMs());
    }

    public PaymentOutcome charge(Payment payment) {
        PaymentMode current = mode.get();
        log.info("gateway charge requested",
                kv("payment_id", payment.getId()),
                kv("booking_id", payment.getBookingId()),
                kv("amount", payment.getAmount()),
                kv("mode", current));

        return switch (current) {
            case SUCCESS -> PaymentOutcome.succeeded();
            case FAIL -> PaymentOutcome.failed(DECLINE_REASON);
            case SLOW -> {
                pause(delayMs.get());
                yield PaymentOutcome.succeeded();
            }
        };
    }

    public PaymentMode getMode() {
        return mode.get();
    }

    public long getDelayMs() {
        return delayMs.get();
    }

    public void update(PaymentMode newMode, Long newDelayMs) {
        mode.set(newMode);
        if (newDelayMs != null) {
            delayMs.set(newDelayMs);
        }
        log.info("simulation settings changed", kv("mode", newMode), kv("delay_ms", delayMs.get()));
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
