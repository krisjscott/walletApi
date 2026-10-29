package main.digitalwalletapi.dto;
import java.math.BigDecimal;
import main.digitalwalletapi.model.Wallet;
public record WalletResponse(Long id, BigDecimal balance) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getBalance());
    }
}
