package com.eventticket.catalog.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID eventId,
        String name,
        String location,
        Instant startsAt,
        boolean published,
        List<TicketTypeResponse> ticketTypes) { }
