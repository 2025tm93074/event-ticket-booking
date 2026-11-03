package com.eventticket.payment.service;

import com.eventticket.payment.api.dto.CreatePaymentRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of the canonical request payload. Stored next to the Idempotency-Key so that
 * reusing a key with a different payload can be detected (BR8).
 */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    public static String of(CreatePaymentRequest request) {
        // Normalise the amount so 1500, 1500.0 and 1500.00 produce the same fingerprint.
        String canonical = request.bookingId().trim()
                + "|" + request.amount().stripTrailingZeros().toPlainString()
                + "|" + request.currency().trim().toUpperCase();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
