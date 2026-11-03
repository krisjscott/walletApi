package main.digitalwalletapi.dto;
import java.math.BigDecimal;
import java.time.Instant;
import main.digitalwalletapi.model.*;
public record TransactionResponse(Long id, Long fromWalletId, Long toWalletId, BigDecimal amount,
        TransactionType type, TransactionStatus status, Instant createdAt) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(transaction.getId(),
            transaction.getFromWallet() == null ? null : transaction.getFromWallet().getId(),
            transaction.getToWallet().getId(), transaction.getAmount(), transaction.getType(),
            transaction.getStatus(), transaction.getCreatedAt());
    }
}
