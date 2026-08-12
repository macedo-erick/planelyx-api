package com.planelyx.api.exception;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Levels are chosen by who is at fault. A 404 or a validation failure is the caller's
 * problem and routine, so it is DEBUG and off by default; a 403 or a 409 is worth
 * seeing without turning DEBUG on; a database failure is ours and is ERROR.
 *
 * There is deliberately no {@code @ExceptionHandler(Exception.class)}: it would sit in
 * front of Spring's own handling and turn 405s, 415s and unmatched-route 404s into
 * 500s. Genuinely unhandled exceptions keep reaching the container, which logs them
 * with a stack trace already.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        log.debug("404: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        log.warn("403: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        log.info("409: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException ex) {
        log.debug("400: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        log.debug("400 validation: {}", message);

        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(DataAccessException ex) {
        log.error("500: database access failed", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(body);
    }
}
