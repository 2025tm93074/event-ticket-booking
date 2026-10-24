package com.eventticket.catalog.service;

import com.eventticket.catalog.api.dto.EventResponse;
import com.eventticket.catalog.api.error.ApiException;
import com.eventticket.catalog.repository.CatalogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CatalogService {
    private final CatalogRepository repository;

    public CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    public List<EventResponse> getPublishedEvents() {
        return repository.findPublishedEvents();
    }

    public EventResponse getPublishedEvent(UUID eventId) {
        return repository.findPublishedEvent(eventId).orElseThrow(() ->
                ApiException.notFound("EVENT_NOT_FOUND", "No published event exists with id " + eventId));
    }
}
