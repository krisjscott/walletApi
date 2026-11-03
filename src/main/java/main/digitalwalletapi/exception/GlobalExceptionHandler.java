package main.digitalwalletapi.exception;

import org.springframework.http.*;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> missing(ResourceNotFoundException e) {
        return error(404, e.getMessage());
    }

    @ExceptionHandler({IdempotencyConflictException.class, DataIntegrityViolationException.class})
    ResponseEntity<ErrorResponse> conflict(RuntimeException e) {
        return error(409, e instanceof IdempotencyConflictException ? e.getMessage() : "Resource already exists or conflicts with stored data");
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ErrorResponse> busy(RuntimeException e) {
        return error(409, "Concurrent update; retry the request with the same idempotency key");
    }

    @ExceptionHandler({InsufficientBalanceException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> invalid(RuntimeException e) {
        return error(400, e.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> unauthorized(AuthenticationException e) {
        return error(401, "Invalid email or password");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .sorted()
                .collect(java.util.stream.Collectors.joining("; "));
        return error(400, message);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return error(405, "Method not allowed");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException e) {
        return error(400, "Malformed request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> TypeMismatch(MethodArgumentTypeMismatchException e) {
        return error(400, e.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(int status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status, message));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        return error(500, "An unexpected error occurred");
    }
}
