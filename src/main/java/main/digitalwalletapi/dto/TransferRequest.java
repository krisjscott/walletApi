package main.digitalwalletapi.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record TransferRequest(
    @NotNull @Positive Long toWalletId,
    @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
    @NotBlank @Size(max = 100) String idempotencyKey) {}
