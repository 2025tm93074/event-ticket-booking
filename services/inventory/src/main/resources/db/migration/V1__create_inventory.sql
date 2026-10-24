CREATE TABLE ticket_inventory (
    ticket_type_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    total_capacity INTEGER NOT NULL CHECK (total_capacity >= 0),
    available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0 AND available_quantity <= total_capacity),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    booking_id VARCHAR(64) NOT NULL UNIQUE,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    request_fingerprint CHAR(64) NOT NULL,
    response_body TEXT,
    event_id UUID NOT NULL,
    ticket_type_id UUID NOT NULL REFERENCES ticket_inventory(ticket_type_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_reservations_ticket_type ON reservations(ticket_type_id);
CREATE INDEX idx_reservations_status ON reservations(status);

-- General Admission has capacity 5 for the mandatory 20-request concurrency demo.
INSERT INTO ticket_inventory (ticket_type_id, event_id, total_capacity, available_quantity) VALUES
  ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 5, 5),
  ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 2, 2);
