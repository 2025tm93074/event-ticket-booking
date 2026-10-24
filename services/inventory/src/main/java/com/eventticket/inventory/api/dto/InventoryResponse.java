package com.eventticket.inventory.api.dto;

import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(
        UUID ticketTypeId,
        UUID eventId,
        int totalCapacity,
        int availableQuantity,
        Instant updatedAt) { }
