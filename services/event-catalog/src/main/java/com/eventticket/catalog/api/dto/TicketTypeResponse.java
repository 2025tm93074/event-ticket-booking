package com.eventticket.catalog.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketTypeResponse(
        UUID ticketTypeId,
        String name,
        BigDecimal price,
        String currency,
        boolean published) { }
