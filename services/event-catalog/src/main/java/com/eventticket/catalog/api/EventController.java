package com.eventticket.catalog.api;

import com.eventticket.catalog.api.dto.EventResponse;
import com.eventticket.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events")
public class EventController {
    private final CatalogService catalogService;

    public EventController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @Operation(summary = "List published events with published ticket types")
    public List<EventResponse> listEvents() {
        return catalogService.getPublishedEvents();
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get a published event and its valid ticket types")
    public EventResponse getEvent(@PathVariable UUID eventId) {
        return catalogService.getPublishedEvent(eventId);
    }
}
