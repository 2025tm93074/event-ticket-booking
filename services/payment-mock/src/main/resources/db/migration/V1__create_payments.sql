-- One row per payment attempt. booking_id is a plain reference to the Booking service;
-- there is no foreign key because bookings live in a different database.
CREATE TABLE payments (
    id                  UUID           PRIMARY KEY,
    booking_id          VARCHAR(64)    NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3)     NOT NULL,
    status              VARCHAR(16)    NOT NULL CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED')),
    failure_reason      VARCHAR(255),
    idempotency_key     VARCHAR(100)   NOT NULL,
    request_fingerprint VARCHAR(64)    NOT NULL,
    response_body       TEXT,
    created_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key)
);

-- A booking can have at most one payment that is in flight or has succeeded.
-- Failed attempts do not count, so a booking can be retried after a decline.
CREATE UNIQUE INDEX uq_payments_booking_active
    ON payments (booking_id)
    WHERE status IN ('PROCESSING', 'SUCCEEDED');
