package com.eventticket.inventory.api;

import com.eventticket.inventory.api.dto.CreateReservationRequest;
import com.eventticket.inventory.api.dto.InventoryResponse;
import com.eventticket.inventory.api.dto.ReservationResponse;
import com.eventticket.inventory.api.dto.ReservationResult;
import com.eventticket.inventory.api.error.ApiException;
import com.eventticket.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Inventory and reservations")
public class ReservationController {
    private final InventoryService inventoryService;

    public ReservationController(InventoryService inventoryService) { this.inventoryService = inventoryService; }

    @GetMapping("/inventory/{ticketTypeId}")
    @Operation(summary = "Get current inventory for a ticket type")
    public InventoryResponse getInventory(@PathVariable UUID ticketTypeId) {
        return inventoryService.getInventory(ticketTypeId).orElseThrow(() ->
                ApiException.notFound("TICKET_TYPE_NOT_FOUND", "No inventory exists for ticket type " + ticketTypeId));
    }

    @PostMapping("/reservations")
    @Operation(summary = "Atomically reserve tickets for a booking")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
            description = "Unique key per logical reservation; same key and same body replays the stored response")
    public ResponseEntity<ReservationResponse> reserve(
            @Parameter(hidden = true) @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateReservationRequest request) {
        String key = validateIdempotencyKey(idempotencyKey);
        ReservationResult result = inventoryService.reserve(key, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replay", String.valueOf(result.replayed())).body(result.response());
    }

    @GetMapping("/reservations/{reservationId}")
    @Operation(summary = "Get reservation state")
    public ReservationResponse getReservation(@PathVariable UUID reservationId) {
        // Reuse the service's safe not-found lookup through a read-only status transition-free path.
        return inventoryService.getReservation(reservationId);
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    @Operation(summary = "Confirm a reservation after successful payment")
    public ReservationResponse confirm(@PathVariable UUID reservationId) {
        return inventoryService.confirm(reservationId);
    }

    @PostMapping("/reservations/{reservationId}/release")
    @Operation(summary = "Release an unconfirmed reservation safely; repeated calls do not add capacity twice")
    public ReservationResponse release(@PathVariable UUID reservationId) {
        return inventoryService.release(reservationId);
    }

    private static String validateIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw ApiException.badRequest("MISSING_IDEMPOTENCY_KEY", "Header Idempotency-Key is required");
        }
        String key = rawKey.trim();
        if (key.length() > 100) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be at most 100 characters");
        }
        return key;
    }
}
