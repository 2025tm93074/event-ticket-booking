package com.eventticket.inventory.service;

import com.eventticket.inventory.api.dto.CreateReservationRequest;
import com.eventticket.inventory.api.dto.ReservationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.eventticket.inventory.api.error.ApiException;
import com.eventticket.inventory.repository.InventoryRepository;
import com.eventticket.inventory.repository.InventoryRepository.ReservationRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock InventoryRepository repository;
    InventoryService service;
    ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        // Executes service transaction callbacks synchronously without opening a database connection.
        mapper = new ObjectMapper().findAndRegisterModules().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        service = new InventoryService(repository, new DirectTransactionManager(), mapper);
    }

    @Test
    void insufficientCapacityIsRejectedWithoutCreatingReservation() {
        CreateReservationRequest request = new CreateReservationRequest("booking-capacity-test",
                UUID.randomUUID(), UUID.randomUUID(), 1);
        when(repository.findByIdempotencyKey("capacity-key")).thenReturn(Optional.empty());
        when(repository.findByBookingId(request.bookingId())).thenReturn(Optional.empty());
        when(repository.decrementCapacity(request.ticketTypeId(), request.eventId(), 1)).thenReturn(0);
        when(repository.ticketTypeBelongsToEvent(request.ticketTypeId(), request.eventId())).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class, () -> service.reserve("capacity-key", request));
        assertEquals("INSUFFICIENT_INVENTORY", ex.getCode());
        verify(repository, never()).insertReservation(any(), any(), anyString(), anyString());
    }

    @Test
    void sameIdempotencyKeyAndPayloadReplaysStoredResponse() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest("booking-replay-test",
                UUID.randomUUID(), UUID.randomUUID(), 1);
        UUID reservationId = UUID.randomUUID();
        ReservationResponse response = new ReservationResponse(reservationId, request.bookingId(),
                request.eventId(), request.ticketTypeId(), 1, "RESERVED", Instant.now(), Instant.now());
        String key = "reserve-booking-replay-test";
        String fingerprint = fingerprint(request);
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(
                new ReservationRecord(response, key, fingerprint, mapper.writeValueAsString(response))));

        var result = service.reserve(key, request);

        assertTrue(result.replayed());
        assertEquals(response, result.response());
        verify(repository, never()).decrementCapacity(any(), any(), anyInt());
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsRejected() throws Exception {
        CreateReservationRequest original = new CreateReservationRequest("booking-conflict-test",
                UUID.randomUUID(), UUID.randomUUID(), 1);
        ReservationResponse response = new ReservationResponse(UUID.randomUUID(), original.bookingId(),
                original.eventId(), original.ticketTypeId(), 1, "RESERVED", Instant.now(), Instant.now());
        String key = "reserve-booking-conflict-test";
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(
                new ReservationRecord(response, key, fingerprint(original), mapper.writeValueAsString(response))));
        CreateReservationRequest changed = new CreateReservationRequest(original.bookingId(),
                original.eventId(), original.ticketTypeId(), 2);

        ApiException ex = assertThrows(ApiException.class, () -> service.reserve(key, changed));

        assertEquals("IDEMPOTENCY_KEY_REUSED", ex.getCode());
        verify(repository, never()).decrementCapacity(any(), any(), anyInt());
    }

    private String fingerprint(CreateReservationRequest request) throws Exception {
        String canonical = request.bookingId() + "|" + request.eventId() + "|" + request.ticketTypeId() + "|" + request.quantity();
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void repeatedReleaseDoesNotRestoreCapacityAgain() {
        UUID reservationId = UUID.randomUUID();
        ReservationResponse released = new ReservationResponse(reservationId, "booking-released",
                UUID.randomUUID(), UUID.randomUUID(), 1, "RELEASED", Instant.now(), Instant.now());
        when(repository.findReservation(reservationId)).thenReturn(Optional.of(released));
        assertEquals("RELEASED", service.release(reservationId).status());
        verify(repository, never()).markReleasedIfReserved(any());
        verify(repository, never()).restoreCapacity(any(), anyInt());
    }

    @Test
    void confirmedReservationCannotBeReleased() {
        UUID reservationId = UUID.randomUUID();
        ReservationResponse confirmed = new ReservationResponse(reservationId, "booking-confirmed",
                UUID.randomUUID(), UUID.randomUUID(), 1, "CONFIRMED", Instant.now(), Instant.now());
        when(repository.findReservation(reservationId)).thenReturn(Optional.of(confirmed));
        ApiException ex = assertThrows(ApiException.class, () -> service.release(reservationId));
        assertEquals("RESERVATION_ALREADY_CONFIRMED", ex.getCode());
        verify(repository, never()).restoreCapacity(any(), anyInt());
    }

    /** Executes TransactionTemplate's callback synchronously for unit tests only. */
    private static final class DirectTransactionManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition) {
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }
}
