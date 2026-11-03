package main.digitalwalletapi.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerRegressionTest {
    @Test
    @DisplayName("Issue 11: database integrity conflicts map to HTTP 409 without leaking database details")
    void databaseIntegrityConflictsReturnConflict() {
        var response = new GlobalExceptionHandler().conflict(new DataIntegrityViolationException("private database detail"));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().message()).doesNotContain("private database detail");
    }

    @Test
    @DisplayName("Unhandled exceptions return HTTP 500 with a generic error message")
    void unhandledExceptionsReturnInternalServerError() {
        var response = new GlobalExceptionHandler().handleGeneralException(new RuntimeException("secret stack trace"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("Unsupported HTTP methods return HTTP 405 Method Not Allowed")
    void unsupportedHttpMethodsReturnMethodNotAllowed() {
        var response = new GlobalExceptionHandler().methodNotAllowed(new org.springframework.web.HttpRequestMethodNotSupportedException("POST"));
        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(405);
        assertThat(response.getBody().message()).isEqualTo("Method not allowed");
    }
}
