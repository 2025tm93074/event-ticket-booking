package com.eventticket.catalog.service;

import com.eventticket.catalog.api.dto.EventResponse;
import com.eventticket.catalog.api.dto.TicketTypeResponse;
import com.eventticket.catalog.api.error.ApiException;
import com.eventticket.catalog.repository.CatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {
    @Mock CatalogRepository repository;
    @InjectMocks CatalogService service;

    @Test
    void publishedEventIncludesPublishedTicketTypes() {
        UUID eventId = UUID.randomUUID();
        EventResponse event = new EventResponse(eventId, "Demo", "Pune", Instant.parse("2026-11-14T18:00:00Z"),
                true, List.of(new TicketTypeResponse(UUID.randomUUID(), "General", new BigDecimal("500.00"), "INR", true)));
        when(repository.findPublishedEvent(eventId)).thenReturn(Optional.of(event));
        assertEquals(event, service.getPublishedEvent(eventId));
        verify(repository).findPublishedEvent(eventId);
    }

    @Test
    void unpublishedOrUnknownEventIsRejected() {
        UUID eventId = UUID.randomUUID();
        when(repository.findPublishedEvent(eventId)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> service.getPublishedEvent(eventId));
        assertEquals("EVENT_NOT_FOUND", ex.getCode());
    }
}
