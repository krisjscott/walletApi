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
}
