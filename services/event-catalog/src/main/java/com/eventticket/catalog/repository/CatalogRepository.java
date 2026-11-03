package com.eventticket.catalog.repository;

import com.eventticket.catalog.api.dto.EventResponse;
import com.eventticket.catalog.api.dto.TicketTypeResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EventResponse> findPublishedEvents() {
        List<EventRow> rows = jdbc.query("""
                SELECT id, name, location, starts_at, published
                FROM events WHERE published = TRUE ORDER BY starts_at, name
                """, (rs, rowNum) -> new EventRow(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("location"),
                rs.getTimestamp("starts_at").toInstant(), rs.getBoolean("published")));
        // Finish reading the outer ResultSet before running ticket-type queries on the same connection.
        return rows.stream().map(this::toResponse).toList();
    }

    public Optional<EventResponse> findPublishedEvent(UUID eventId) {
        List<EventRow> rows = jdbc.query("""
                SELECT id, name, location, starts_at, published
                FROM events WHERE id = ? AND published = TRUE
                """, (rs, rowNum) -> new EventRow(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("location"),
                rs.getTimestamp("starts_at").toInstant(), rs.getBoolean("published")), eventId);
        return rows.stream().findFirst().map(this::toResponse);
    }

    private EventResponse toResponse(EventRow event) {
        List<TicketTypeResponse> ticketTypes = jdbc.query("""
                SELECT id, name, price, currency, published
                FROM ticket_types WHERE event_id = ? AND published = TRUE
                ORDER BY price, name
                """, (ticketRs, rowNum) -> new TicketTypeResponse(
                ticketRs.getObject("id", UUID.class),
                ticketRs.getString("name"),
                ticketRs.getBigDecimal("price"),
                ticketRs.getString("currency"),
                ticketRs.getBoolean("published")), event.id());
        return new EventResponse(event.id(), event.name(), event.location(), event.startsAt(),
                event.published(), ticketTypes);
    }

    private record EventRow(UUID id, String name, String location, Instant startsAt, boolean published) { }
}
