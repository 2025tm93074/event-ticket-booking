package com.eventticket.inventory.service;

import com.eventticket.inventory.api.dto.CreateReservationRequest;
import com.eventticket.inventory.api.dto.InventoryResponse;
import com.eventticket.inventory.api.dto.ReservationResponse;
import com.eventticket.inventory.api.dto.ReservationResult;
import com.eventticket.inventory.api.error.ApiException;
import com.eventticket.inventory.repository.InventoryRepository;
import com.eventticket.inventory.repository.InventoryRepository.ReservationRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryService {
    private final InventoryRepository repository;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    public InventoryService(InventoryRepository repository, PlatformTransactionManager transactionManager,
                            ObjectMapper objectMapper) {
        this.repository = repository;
        this.transactions = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    public Optional<InventoryResponse> getInventory(UUID ticketTypeId) {
        return repository.findInventory(ticketTypeId);
    }

    public ReservationResponse getReservation(UUID reservationId) {
        return repository.findReservation(reservationId).orElseThrow(() ->
                ApiException.notFound("RESERVATION_NOT_FOUND", "Reservation not found: " + reservationId));
    }

    /**
     * Idempotency-Key is the API replay key. booking_id is independently unique to prevent a
     * booking from reserving twice under different keys. The response body and request fingerprint
     * are stored in the same transaction as the reservation and capacity decrement.
     */
    public ReservationResult reserve(String idempotencyKey, CreateReservationRequest request) {
        String fingerprint = fingerprint(request);
        try {
            return Objects.requireNonNull(transactions.execute(status ->
                    reserveInTransaction(idempotencyKey, fingerprint, request)));
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request may have won either the idempotency-key or booking-id constraint.
            Optional<ReservationRecord> byKey = repository.findByIdempotencyKey(idempotencyKey);
            if (byKey.isPresent()) return replayOrConflict(byKey.get(), fingerprint);
            if (repository.findByBookingId(request.bookingId()).isPresent()) {
                throw ApiException.conflict("BOOKING_ALREADY_RESERVED",
                        "This booking already has a reservation under a different Idempotency-Key");
            }
            throw ApiException.conflict("RESERVATION_CONFLICT", "Reservation conflicts with existing inventory state");
        }
    }

    private ReservationResult reserveInTransaction(String idempotencyKey, String fingerprint,
                                                   CreateReservationRequest request) {
        Optional<ReservationRecord> existingKey = repository.findByIdempotencyKey(idempotencyKey);
        if (existingKey.isPresent()) return replayOrConflict(existingKey.get(), fingerprint);

        if (repository.findByBookingId(request.bookingId()).isPresent()) {
            throw ApiException.conflict("BOOKING_ALREADY_RESERVED",
                    "This booking already has a reservation under a different Idempotency-Key");
        }

        int updated = repository.decrementCapacity(request.ticketTypeId(), request.eventId(), request.quantity());
        if (updated == 0) {
            // A duplicate request may have committed while this atomic UPDATE waited on the row lock.
            Optional<ReservationRecord> racedKey = repository.findByIdempotencyKey(idempotencyKey);
            if (racedKey.isPresent()) return replayOrConflict(racedKey.get(), fingerprint);
            if (repository.findByBookingId(request.bookingId()).isPresent()) {
                throw ApiException.conflict("BOOKING_ALREADY_RESERVED",
                        "This booking already has a reservation under a different Idempotency-Key");
            }
            if (!repository.ticketTypeBelongsToEvent(request.ticketTypeId(), request.eventId())) {
                throw ApiException.notFound("TICKET_TYPE_NOT_FOUND", "Ticket type does not exist for this event");
            }
            throw ApiException.conflict("INSUFFICIENT_INVENTORY", "Not enough tickets are available for this reservation");
        }

        UUID reservationId = UUID.randomUUID();
        repository.insertReservation(reservationId, request, idempotencyKey, fingerprint);
        ReservationResponse response = repository.findReservation(reservationId).orElseThrow(() ->
                new IllegalStateException("Inserted reservation could not be read back"));
        try {
            repository.storeResponseBody(reservationId, objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not persist reservation response for idempotent replay", ex);
        }
        return new ReservationResult(response, false);
    }

    private ReservationResult replayOrConflict(ReservationRecord record, String fingerprint) {
        if (!record.requestFingerprint().equals(fingerprint)) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with a different reservation payload");
        }
        if (record.responseBody() == null || record.responseBody().isBlank()) {
            throw new IllegalStateException("Stored idempotency response is missing");
        }
        try {
            return new ReservationResult(objectMapper.readValue(record.responseBody(), ReservationResponse.class), true);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored idempotency response could not be parsed", ex);
        }
    }

    private String fingerprint(CreateReservationRequest request) {
        String canonical = request.bookingId() + "|" + request.eventId() + "|" + request.ticketTypeId() + "|" + request.quantity();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public ReservationResponse confirm(UUID reservationId) {
        return Objects.requireNonNull(transactions.execute(status -> {
            ReservationResponse current = repository.findReservation(reservationId).orElseThrow(() ->
                    ApiException.notFound("RESERVATION_NOT_FOUND", "Reservation not found: " + reservationId));
            if ("CONFIRMED".equals(current.status())) return current;
            if ("RELEASED".equals(current.status())) {
                throw ApiException.conflict("RESERVATION_ALREADY_RELEASED", "A released reservation cannot be confirmed");
            }
            repository.markConfirmedIfReserved(reservationId);
            ReservationResponse updated = repository.findReservation(reservationId).orElseThrow();
            if (!"CONFIRMED".equals(updated.status())) {
                throw ApiException.conflict("RESERVATION_STATE_CONFLICT", "Reservation could not be confirmed");
            }
            return updated;
        }));
    }

    public ReservationResponse release(UUID reservationId) {
        return Objects.requireNonNull(transactions.execute(status -> {
            ReservationResponse current = repository.findReservation(reservationId).orElseThrow(() ->
                    ApiException.notFound("RESERVATION_NOT_FOUND", "Reservation not found: " + reservationId));
            if ("RELEASED".equals(current.status())) return current;
            if ("CONFIRMED".equals(current.status())) {
                throw ApiException.conflict("RESERVATION_ALREADY_CONFIRMED", "A confirmed reservation cannot be released");
            }

            int changed = repository.markReleasedIfReserved(reservationId);
            if (changed == 1) {
                int restored = repository.restoreCapacity(current.ticketTypeId(), current.quantity());
                if (restored != 1) throw new IllegalStateException("Inventory row missing while releasing reservation");
                return repository.findReservation(reservationId).orElseThrow();
            }

            // Another concurrent release may have won. Do not restore capacity again.
            ReservationResponse afterRace = repository.findReservation(reservationId).orElseThrow();
            if ("RELEASED".equals(afterRace.status())) return afterRace;
            if ("CONFIRMED".equals(afterRace.status())) {
                throw ApiException.conflict("RESERVATION_ALREADY_CONFIRMED", "A confirmed reservation cannot be released");
            }
            throw ApiException.conflict("RESERVATION_STATE_CONFLICT", "Reservation could not be released");
        }));
    }
}
