package com.eventticket.payment.config;

import com.eventticket.payment.domain.PaymentMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Simulator settings, bound from PAYMENT_MODE, PAYMENT_DELAY_MS and PAYMENT_IN_PROGRESS_WAIT_MS. */
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(PaymentMode mode, long delayMs, long inProgressWaitMs) {
}
