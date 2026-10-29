package main.digitalwalletapi.regression;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import main.digitalwalletapi.dto.AuthResponse;
import main.digitalwalletapi.dto.RegisterRequest;
import main.digitalwalletapi.dto.TransactionResponse;
import main.digitalwalletapi.dto.TransferRequest;
import main.digitalwalletapi.repository.TransactionRepository;
import main.digitalwalletapi.repository.WalletRepository;
import main.digitalwalletapi.service.AuthService;
import main.digitalwalletapi.service.TransactionService;
import main.digitalwalletapi.service.TransferService;
import main.digitalwalletapi.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TransferHistoryIssueRegressionTest {
    @Autowired private AuthService auth;
    @Autowired private TransferService transfers;
    @Autowired private WalletService wallets;
    @Autowired private TransactionService history;
    @Autowired private WalletRepository walletRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    private String senderEmail;
    private String recipientEmail;
    private AuthResponse sender;
    private AuthResponse recipient;

    @BeforeEach
    void setUp() {
        senderEmail = UUID.randomUUID() + "@example.com";
        recipientEmail = UUID.randomUUID() + "@example.com";
        sender = auth.register(new RegisterRequest("Sender", senderEmail, "password123"));
        recipient = auth.register(new RegisterRequest("Recipient", recipientEmail, "password123"));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            walletRepository.findLockedById(sender.walletId()).orElseThrow().credit(new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("Issue 5: balance responses retain cents from committed transfers")
    void balanceResponseRetainsFractionalCurrency() {
        transfer("12.50", "cents");
        assertThat(wallets.balance(senderEmail).balance()).isEqualByComparingTo("87.50");
        assertThat(wallets.balance(recipientEmail).balance()).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("Issue 6: idempotent retries accept equal amounts with different decimal scales")
    void retryWithDifferentScaleReturnsOriginalPayment() {
        var original = transfer("10.00", "same-payment");
        var replay = org.junit.jupiter.api.Assertions.assertDoesNotThrow(
            () -> transfer("10.0", "same-payment"), "Equal monetary amounts must replay the original payment");
        assertThat(replay.id()).isEqualTo(original.id());
        assertThat(walletRepository.findById(sender.walletId()).orElseThrow().getBalance())
            .isEqualByComparingTo("90.00");
        assertThat(walletRepository.findById(recipient.walletId()).orElseThrow().getBalance())
            .isEqualByComparingTo("10.00");
        assertThat(transactionRepository.findByFromWalletIdAndIdempotencyKey(sender.walletId(), "same-payment"))
            .isPresent().get().extracting(transaction -> transaction.getId()).isEqualTo(original.id());
    }

    @Test
    @DisplayName("Issue 7: consecutive history pages contain distinct records and report the requested page")
    void consecutiveHistoryPagesDoNotOverlap() {
        seedThreeDatedPayments();
        var first = history.history(senderEmail, 0, 1);
        var second = history.history(senderEmail, 1, 1);
        assertThat(second.getNumber()).isEqualTo(1);
        assertThat(second.getContent()).extracting(TransactionResponse::id)
            .doesNotContain(first.getContent().getFirst().id());
    }

    @Test
    @DisplayName("Issue 8: history places the most recent timestamp first")
    void historyIsNewestFirst() {
        List<TransactionResponse> payments = seedThreeDatedPayments();
        assertThat(history.history(senderEmail, 0, 10).getContent()).extracting(TransactionResponse::id)
            .containsExactly(payments.get(2).id(), payments.get(1).id(), payments.getFirst().id());
    }

    @Test
    @DisplayName("Issue 9: a recipient can see incoming payments in their own history")
    void historyContainsIncomingPayments() {
        var payment = transfer("10.00", "incoming");
        var received = history.history(recipientEmail, 0, 10);
        assertThat(received.getContent()).extracting(TransactionResponse::id).containsExactly(payment.id());
    }

    private TransactionResponse transfer(String amount, String key) {
        return transfers.transfer(senderEmail, new TransferRequest(recipient.walletId(), new BigDecimal(amount), key));
    }

    private List<TransactionResponse> seedThreeDatedPayments() {
        var payments = List.of(transfer("1.00", "first"), transfer("2.00", "second"), transfer("3.00", "third"));
        // Set distinct persisted dates so sorting tests do not depend on clock resolution or sleeps.
        for (int i = 0; i < payments.size(); i++) {
            jdbc.update("update wallet_transaction set created_at = ? where id = ?",
                LocalDateTime.of(2025, 1, 1, 12, 0).plusDays(i), payments.get(i).id());
        }
        return payments;
    }
}
