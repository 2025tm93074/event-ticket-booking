package com.eventticket.inventory.repository;

import com.eventticket.inventory.api.dto.CreateReservationRequest;
import com.eventticket.inventory.api.dto.InventoryResponse;
import com.eventticket.inventory.api.dto.ReservationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InventoryRepository {
    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<InventoryResponse> findInventory(UUID ticketTypeId) {
        List<InventoryResponse> rows = jdbc.query("""
                SELECT ticket_type_id, event_id, total_capacity, available_quantity, updated_at
                FROM ticket_inventory WHERE ticket_type_id = ?
                """, (rs, rowNum) -> new InventoryResponse(
                rs.getObject("ticket_type_id", UUID.class), rs.getObject("event_id", UUID.class),
                rs.getInt("total_capacity"), rs.getInt("available_quantity"),
                rs.getTimestamp("updated_at").toInstant()), ticketTypeId);
        return rows.stream().findFirst();
    }

    public boolean ticketTypeBelongsToEvent(UUID ticketTypeId, UUID eventId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ticket_inventory WHERE ticket_type_id = ? AND event_id = ?
                """, Integer.class, ticketTypeId, eventId);
        return count != null && count > 0;
    }

    /** Atomic conditional update: the database, not a check-then-update in Java, enforces capacity. */
    public int decrementCapacity(UUID ticketTypeId, UUID eventId, int quantity) {
        return jdbc.update("""
                UPDATE ticket_inventory
                SET available_quantity = available_quantity - ?, updated_at = CURRENT_TIMESTAMP
                WHERE ticket_type_id = ? AND event_id = ? AND available_quantity >= ?
                """, quantity, ticketTypeId, eventId, quantity);
    }

    public void insertReservation(UUID id, CreateReservationRequest request,
                                  String idempotencyKey, String fingerprint) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO reservations (id, booking_id, idempotency_key, request_fingerprint, response_body,
                                          event_id, ticket_type_id, quantity, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, NULL, ?, ?, ?, 'RESERVED', ?, ?)
                """, id, request.bookingId(), idempotencyKey, fingerprint, request.eventId(),
                request.ticketTypeId(), request.quantity(), Timestamp.from(now), Timestamp.from(now));
    }

    public void storeResponseBody(UUID reservationId, String responseBody) {
        jdbc.update("UPDATE reservations SET response_body = ? WHERE id = ?", responseBody, reservationId);
    }

    public Optional<ReservationResponse> findReservation(UUID reservationId) {
        return queryReservation("""
                SELECT id, booking_id, idempotency_key, request_fingerprint, response_body,
                       event_id, ticket_type_id, quantity, status, created_at, updated_at
                FROM reservations WHERE id = ?
                """, reservationId).map(ReservationRecord::reservation);
    }

    public Optional<ReservationRecord> findByBookingId(String bookingId) {
        return queryReservation("""
                SELECT id, booking_id, idempotency_key, request_fingerprint, response_body,
                       event_id, ticket_type_id, quantity, status, created_at, updated_at
                FROM reservations WHERE booking_id = ?
                """, bookingId);
    }

    public Optional<ReservationRecord> findByIdempotencyKey(String idempotencyKey) {
        return queryReservation("""
                SELECT id, booking_id, idempotency_key, request_fingerprint, response_body,
                       event_id, ticket_type_id, quantity, status, created_at, updated_at
                FROM reservations WHERE idempotency_key = ?
                """, idempotencyKey);
    }

    private Optional<ReservationRecord> queryReservation(String sql, Object arg) {
        List<ReservationRecord> rows = jdbc.query(sql, (rs, rowNum) -> {
            ReservationResponse response = new ReservationResponse(
                    rs.getObject("id", UUID.class), rs.getString("booking_id"),
                    rs.getObject("event_id", UUID.class), rs.getObject("ticket_type_id", UUID.class),
                    rs.getInt("quantity"), rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
            return new ReservationRecord(response, rs.getString("idempotency_key"),
                    rs.getString("request_fingerprint"), rs.getString("response_body"));
        }, arg);
        return rows.stream().findFirst();
    }

    public int markReleasedIfReserved(UUID reservationId) {
        return jdbc.update("""
                UPDATE reservations SET status = 'RELEASED', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'RESERVED'
                """, reservationId);
    }

    public int markConfirmedIfReserved(UUID reservationId) {
        return jdbc.update("""
                UPDATE reservations SET status = 'CONFIRMED', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'RESERVED'
                """, reservationId);
    }

    public int restoreCapacity(UUID ticketTypeId, int quantity) {
        return jdbc.update("""
                UPDATE ticket_inventory
                SET available_quantity = LEAST(total_capacity, available_quantity + ?), updated_at = CURRENT_TIMESTAMP
                WHERE ticket_type_id = ?
                """, quantity, ticketTypeId);
    }

    public record ReservationRecord(ReservationResponse reservation, String idempotencyKey,
                                    String requestFingerprint, String responseBody) { }
}
