package com.eventticket.inventory.api.error;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, int status, String error, String code,
                       String message, String path, String correlationId,
                       List<FieldViolation> details) {
    public record FieldViolation(String field, String message) { }
}
