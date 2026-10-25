package main.digitalwalletapi.integration;
import main.digitalwalletapi.dto.*;
import main.digitalwalletapi.repository.*;
import main.digitalwalletapi.service.*;
import main.digitalwalletapi.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest
@ActiveProfiles("test")
class TransferIntegrationTest {
    @Autowired AuthService auth;
    @Autowired TransferService transfers;
    @Autowired WalletService wallets;
    @Autowired TransactionService history;
    @Autowired WalletRepository walletRepository;
    @Autowired PlatformTransactionManager transactionManager;
    private String senderEmail;
    private String recipientEmail;
    private AuthResponse sender;
    private AuthResponse recipient;
    @BeforeEach void setup() {
        senderEmail = UUID.randomUUID() + "@example.com";
        recipientEmail = UUID.randomUUID() + "@example.com";
        sender = auth.register(new RegisterRequest("Sender", senderEmail, "password123"));
        recipient = auth.register(new RegisterRequest("Recipient", recipientEmail, "password123"));
        // Seed funds in the database for tests. No public endpoint creates money.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            walletRepository.findLockedById(sender.walletId()).orElseThrow().credit(new BigDecimal("100.00")));
    }
    @Test void transferUpdatesBalancesAndBothHistoriesAndReplaysOnce() {
        var request = new TransferRequest(recipient.walletId(), new BigDecimal("25.00"), "once");
        var result = transfers.transfer(senderEmail, request);
        assertThat(transfers.transfer(senderEmail, request).id()).isEqualTo(result.id());
        assertThat(wallets.balance(senderEmail).balance()).isEqualByComparingTo("75.00");
        assertThat(wallets.balance(recipientEmail).balance()).isEqualByComparingTo("25.00");
        assertThat(history.history(senderEmail, 0, 20).getTotalElements()).isEqualTo(1);
        assertThat(history.history(recipientEmail, 0, 20).getContent().getFirst().id()).isEqualTo(result.id());
        var strangerEmail = UUID.randomUUID() + "@example.com";
        auth.register(new RegisterRequest("Stranger", strangerEmail, "password123"));
        assertThat(history.history(strangerEmail, 0, 20)).isEmpty();
        assertThatThrownBy(() -> transfers.transfer(senderEmail,
            new TransferRequest(recipient.walletId(), new BigDecimal("26.00"), "once")))
            .isInstanceOf(IdempotencyConflictException.class);
        // Keys belong to each sender, so another wallet can use the same key.
        transfers.transfer(recipientEmail, new TransferRequest(sender.walletId(), BigDecimal.ONE, "once"));
    }
    @Test void insufficientFundsRollsBackEverything() {
        assertThatThrownBy(() -> transfers.transfer(senderEmail,
            new TransferRequest(recipient.walletId(), new BigDecimal("101.00"), "too-much")))
            .isInstanceOf(InsufficientBalanceException.class);
        assertThat(wallets.balance(senderEmail).balance()).isEqualByComparingTo("100.00");
        assertThat(wallets.balance(recipientEmail).balance()).isZero();
        assertThat(history.history(senderEmail, 0, 20)).isEmpty();
    }
    @Test void recipientOverflowAlsoRollsBackSenderDebit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            walletRepository.findLockedById(recipient.walletId()).orElseThrow().credit(new BigDecimal("999999999999.99")));
        assertThatThrownBy(() -> transfers.transfer(senderEmail,
            new TransferRequest(recipient.walletId(), BigDecimal.ONE, "overflow"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(wallets.balance(senderEmail).balance()).isEqualByComparingTo("100.00");
        assertThat(history.history(senderEmail, 0, 20)).isEmpty();
    }
    @Test void concurrentRetriesCreateOneTransfer() throws Exception {
        var results = concurrently(() -> transfers.transfer(senderEmail,
            new TransferRequest(recipient.walletId(), new BigDecimal("10.00"), "same-key")).id());
        assertThat(new HashSet<>(results)).hasSize(1);
        assertThat(wallets.balance(senderEmail).balance()).isEqualByComparingTo("90.00");
        assertThat(wallets.balance(recipientEmail).balance()).isEqualByComparingTo("10.00");
        assertThat(history.history(senderEmail, 0, 20).getTotalElements()).isEqualTo(1);
    }
    @Test void concurrentDebitsCannotOverspend() throws Exception {
        var outcomes = concurrently(() -> {
            try {
                transfers.transfer(senderEmail, new TransferRequest(recipient.walletId(), new BigDecimal("30.00"), UUID.randomUUID().toString()));
                return true;
            } catch (InsufficientBalanceException e) { return false; }
        });
        assertThat(outcomes.stream().filter(Boolean::booleanValue).count()).isEqualTo(3);
        assertThat(wallets.balance(senderEmail).balance()).isEqualByComparingTo("10.00");
        assertThat(wallets.balance(recipientEmail).balance()).isEqualByComparingTo("90.00");
    }
    private <T> List<T> concurrently(Callable<T> task) throws Exception {
        try (var executor = Executors.newFixedThreadPool(6)) {
            var start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) futures.add(executor.submit(() -> { start.await(); return task.call(); }));
            start.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        }
    }
}
