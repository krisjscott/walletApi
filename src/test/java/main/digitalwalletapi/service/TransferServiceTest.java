package main.digitalwalletapi.service;
import main.digitalwalletapi.model.*;
import main.digitalwalletapi.dto.*;
import main.digitalwalletapi.repository.*;
import main.digitalwalletapi.exception.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class TransferServiceTest {
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final TransferService transfers = new TransferService(wallets, transactions);
    @Test void rejectsSelfTransfer() {
        when(wallets.findIdByUserEmail("alice@example.com")).thenReturn(Optional.of(1L));
        assertThatThrownBy(() -> transfers.transfer("alice@example.com", new TransferRequest(1L, BigDecimal.ONE, "key")))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transactions);
    }
    @Test void rejectsMissingRecipient() {
        when(wallets.findIdByUserEmail("alice@example.com")).thenReturn(Optional.of(1L));
        when(wallets.findLockedById(1L)).thenReturn(Optional.of(new Wallet(new User("Alice", "alice@example.com", "hash"))));
        assertThatThrownBy(() -> transfers.transfer("alice@example.com", new TransferRequest(2L, BigDecimal.ONE, "key")))
            .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void walletRefusesOverdraftAndFractionalCents() {
        Wallet wallet = new Wallet(new User("Alice", "alice@example.com", "hash"));
        assertThatThrownBy(() -> wallet.debit(BigDecimal.ONE)).isInstanceOf(InsufficientBalanceException.class);
        assertThatThrownBy(() -> wallet.credit(new BigDecimal("0.001"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(wallet.getBalance()).isZero();
    }
}
