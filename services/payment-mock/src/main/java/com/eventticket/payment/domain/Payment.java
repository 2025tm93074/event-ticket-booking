package com.eventticket.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_id", nullable = false, length = 64)
    private String bookingId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "idempotency_key", nullable = false, length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
        // for JPA
    }

    /** Creates a new attempt that claims the idempotency key before the gateway is called. */
    public static Payment processing(String idempotencyKey, String requestFingerprint,
                                     String bookingId, BigDecimal amount, String currency) {
        Payment payment = new Payment();
        payment.idempotencyKey = idempotencyKey;
        payment.requestFingerprint = requestFingerprint;
        payment.bookingId = bookingId;
        payment.amount = amount;
        payment.currency = currency;
        payment.status = PaymentStatus.PROCESSING;
        payment.createdAt = Instant.now();
        payment.updatedAt = payment.createdAt;
        return payment;
    }

    /** Moves the payment to its terminal state. Only allowed once, from PROCESSING. */
    public void complete(PaymentOutcome outcome) {
        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Payment " + id + " is already " + status);
        }
        if (!outcome.status().isTerminal()) {
            throw new IllegalArgumentException("Outcome must be SUCCEEDED or FAILED");
        }
        this.status = outcome.status();
        this.failureReason = outcome.failureReason();
        this.updatedAt = Instant.now();
    }

    /** Stores the exact response body so repeated requests can be answered with it. */
    public void storeResponse(String responseBody) {
        this.responseBody = responseBody;
    }

    public boolean matchesFingerprint(String fingerprint) {
        return requestFingerprint.equals(fingerprint);
    }

    public UUID getId() {
        return id;
    }

    public String getBookingId() {
        return bookingId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
