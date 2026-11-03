package com.eventticket.inventory.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateReservationRequest(
        @NotBlank @Size(max = 64) String bookingId,
        @NotNull UUID eventId,
        @NotNull UUID ticketTypeId,
        @Min(1) @Max(100) int quantity) { }
