package com.eventticket.payment.service;

import com.eventticket.payment.api.dto.CreatePaymentRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFingerprintTest {

    @Test
    void identicalPayloadsHaveSameFingerprint() {
        CreatePaymentRequest a = new CreatePaymentRequest("b-1", new BigDecimal("100.00"), "INR");
        CreatePaymentRequest b = new CreatePaymentRequest("b-1", new BigDecimal("100"), "INR");

        assertThat(RequestFingerprint.of(a)).isEqualTo(RequestFingerprint.of(b)).hasSize(64);
    }

    @Test
    void anyFieldChangeChangesFingerprint() {
        String base = RequestFingerprint.of(new CreatePaymentRequest("b-1", new BigDecimal("100.00"), "INR"));

        assertThat(RequestFingerprint.of(new CreatePaymentRequest("b-2", new BigDecimal("100.00"), "INR"))).isNotEqualTo(base);
        assertThat(RequestFingerprint.of(new CreatePaymentRequest("b-1", new BigDecimal("100.01"), "INR"))).isNotEqualTo(base);
        assertThat(RequestFingerprint.of(new CreatePaymentRequest("b-1", new BigDecimal("100.00"), "USD"))).isNotEqualTo(base);
    }
}
