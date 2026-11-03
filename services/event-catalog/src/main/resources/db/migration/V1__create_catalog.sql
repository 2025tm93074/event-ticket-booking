CREATE TABLE events (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    location VARCHAR(160) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ticket_types (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    name VARCHAR(100) NOT NULL,
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    published BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_ticket_type_name_per_event UNIQUE (event_id, name)
);

-- Stable demo IDs are documented in docs/shreya-integration-contract.md.
INSERT INTO events (id, name, location, starts_at, published) VALUES
  ('10000000-0000-0000-0000-000000000001', 'Scalable Services Demo Night', 'Pune, India', '2026-11-14T18:00:00Z', TRUE);

INSERT INTO ticket_types (id, event_id, name, price, currency, published) VALUES
  ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'General Admission', 500.00, 'INR', TRUE),
  ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'VIP', 1500.00, 'INR', TRUE);
