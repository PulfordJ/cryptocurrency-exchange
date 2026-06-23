package com.exchange.mock.api;

import java.time.Instant;
import java.util.List;

/** Structured error body returned for all non-2xx REST responses. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details) {

    public static ErrorResponse of(int status, String error, String message, List<String> details) {
        return new ErrorResponse(Instant.now(), status, error, message,
                details == null ? List.of() : details);
    }
}
