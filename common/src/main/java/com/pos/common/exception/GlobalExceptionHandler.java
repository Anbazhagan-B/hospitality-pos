package com.pos.common.exception;

import com.pos.common.dto.ApiResponse;
import com.pos.common.logging.SensitiveDataMasker;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception handling and error logging.
 *
 * <p>Log levels here are chosen for what they mean downstream, not for how bad
 * they feel. Every handler below except the last one represents a <em>client</em>
 * mistake — a bad id, a failed validation, a wrong password. Logging those at
 * ERROR makes an "error rate" panel in Kibana measure how often users typo a
 * check number, and makes any alert built on {@code level: ERROR} fire
 * constantly until someone mutes it. WARN keeps them searchable without
 * poisoning the signal.
 *
 * <p>ERROR is reserved for the one case that means the system itself failed and
 * a human should look.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_TYPE = "errorType";
    private static final String STATUS = "responseStatus";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {} {} {}",
                SensitiveDataMasker.mask(ex.getMessage()),
                StructuredArguments.keyValue(ERROR_TYPE, "RESOURCE_NOT_FOUND"),
                StructuredArguments.keyValue(STATUS, 404));
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(BadRequestException ex) {
        log.warn("Bad request: {} {} {}",
                SensitiveDataMasker.mask(ex.getMessage()),
                StructuredArguments.keyValue(ERROR_TYPE, "BAD_REQUEST"),
                StructuredArguments.keyValue(STATUS, 400));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(BadCredentialsException ex) {
        // Deliberately does not log the submitted credentials. A failed login is
        // a security event worth counting — repeated occurrences for one user or
        // source address are what a brute-force alert is built on.
        log.warn("Authentication failed {} {}",
                StructuredArguments.keyValue(ERROR_TYPE, "AUTH_FAILED"),
                StructuredArguments.keyValue(STATUS, 401));
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid credentials"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        // An authenticated user reaching for something they are not entitled to
        // is a genuine security signal, so this one keeps a higher level than
        // the other client errors.
        log.warn("Access denied: {} {} {}",
                SensitiveDataMasker.mask(ex.getMessage()),
                StructuredArguments.keyValue(ERROR_TYPE, "ACCESS_DENIED"),
                StructuredArguments.keyValue(STATUS, 403));
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        // Only the field names and messages are logged, never the rejected
        // values — those are user input and may contain card data.
        log.warn("Validation failed {} {} {}",
                StructuredArguments.keyValue("invalidFields", errors.keySet()),
                StructuredArguments.keyValue(ERROR_TYPE, "VALIDATION_FAILED"),
                StructuredArguments.keyValue(STATUS, 400));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException ex) {
        log.warn("Constraint violation: {} {} {}",
                SensitiveDataMasker.mask(ex.getMessage()),
                StructuredArguments.keyValue(ERROR_TYPE, "CONSTRAINT_VIOLATION"),
                StructuredArguments.keyValue(STATUS, 400));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllUncaughtException(Exception ex) {
        // The only true ERROR here. Passing the throwable is what populates the
        // stack_trace field in Elasticsearch.
        log.error("Unexpected error {} {}",
                StructuredArguments.keyValue(ERROR_TYPE, ex.getClass().getSimpleName()),
                StructuredArguments.keyValue(STATUS, 500),
                ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }
}
