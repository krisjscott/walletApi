package main.digitalwalletapi.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record TransactionHistoryResponse(List<TransactionResponse> content, int page, int size,
                                         long totalElements, int totalPages) {
    public static TransactionHistoryResponse from(Page<TransactionResponse> transactions) {
        return new TransactionHistoryResponse(transactions.getContent(), transactions.getNumber(),
            transactions.getSize(), transactions.getNumberOfElements(), transactions.getTotalPages());
    }
}
