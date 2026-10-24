package com.eventticket.inventory.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        String bookingId,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        String status,
        Instant createdAt,
        Instant updatedAt) { }
