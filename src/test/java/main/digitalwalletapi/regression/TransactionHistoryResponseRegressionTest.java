package main.digitalwalletapi.regression;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import main.digitalwalletapi.dto.TransactionHistoryResponse;
import main.digitalwalletapi.dto.TransactionResponse;
import main.digitalwalletapi.model.TransactionStatus;
import main.digitalwalletapi.model.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.*;

class TransactionHistoryResponseRegressionTest {
    @Test
    @DisplayName("Issue 10: a partial last page preserves the total count across all pages")
    void totalElementsIncludesRecordsOutsideTheCurrentPage() {
        var content = List.of(payment(21L), payment(22L), payment(23L));
        var response = TransactionHistoryResponse.from(new PageImpl<>(content, PageRequest.of(2, 10), 23));
        assertThat(response.totalElements()).isEqualTo(23);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.content()).containsExactlyElementsOf(content);
    }

    private TransactionResponse payment(Long id) {
        return new TransactionResponse(id, 1L, 2L, BigDecimal.ONE,
            TransactionType.TRANSFER, TransactionStatus.SUCCESS, Instant.parse("2025-01-01T12:00:00Z"));
    }
}
