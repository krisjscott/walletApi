package main.digitalwalletapi.regression;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;
import main.digitalwalletapi.dto.AuthResponse;
import main.digitalwalletapi.dto.RegisterRequest;
import main.digitalwalletapi.dto.TransferRequest;
import main.digitalwalletapi.repository.UserRepository;
import main.digitalwalletapi.repository.WalletRepository;
import main.digitalwalletapi.service.AuthService;
import main.digitalwalletapi.service.TransactionService;
import main.digitalwalletapi.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HttpIssueRegressionTest {
    @Autowired private MockMvc mvc;
    @Autowired private AuthService auth;
    @Autowired private TransferService transfers;
    @Autowired private TransactionService history;
    @Autowired private UserRepository users;
    @Autowired private WalletRepository wallets;
    @Autowired private PlatformTransactionManager transactionManager;
    private String senderEmail;
    private AuthResponse sender;
    private AuthResponse recipient;

    @BeforeEach
    void setUp() {
        senderEmail = UUID.randomUUID() + "@example.com";
        sender = auth.register(new RegisterRequest("Sender", senderEmail, "password123"));
        recipient = auth.register(new RegisterRequest("Recipient", UUID.randomUUID() + "@example.com", "password123"));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            wallets.findLockedById(sender.walletId()).orElseThrow().credit(new BigDecimal("100.00")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "1234567"})
    @DisplayName("Issue 4: registration rejects short passwords with an otherwise valid request")
    void shortPasswordsAreRejected(String password) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType("application/json").content("""
            {"name":"Alice","email":"%s","password":"%s"}
            """.formatted(email, password)))
            .andExpect(status().isBadRequest());
        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("Issue 11: duplicate registration returns HTTP 409")
    void duplicateRegistrationReturnsConflict() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("""
            {"name":"Sender","email":"%s","password":"password123"}
            """.formatted(senderEmail)))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Issue 11: a mismatched idempotent retry returns HTTP 409")
    void idempotencyConflictReturnsConflict() throws Exception {
        transfers.transfer(senderEmail, new TransferRequest(recipient.walletId(), new BigDecimal("10.00"), "conflicting"));
        mvc.perform(post("/api/transaction/transfer").header("Authorization", "Bearer " + sender.token())
                .contentType("application/json").content("""
                    {"toWalletId":%d,"amount":11.00,"idempotencyKey":"conflicting"}
                    """.formatted(recipient.walletId())))
            .andExpect(status().isConflict());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTransfers")
    @DisplayName("Issue 12: invalid transfer DTOs return HTTP 400 without side effects")
    void invalidTransferRequestsAreRejected(String description, String json) throws Exception {
        String field = description.contains("recipient") ? "toWalletId"
            : description.contains("amount") ? "amount" : "idempotencyKey";
        mvc.perform(post("/api/transaction/transfer").header("Authorization", "Bearer " + sender.token())
                .contentType("application/json").content(json.replace("RECIPIENT", recipient.walletId().toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value(containsString(field)));
        assertThat(wallets.findById(sender.walletId()).orElseThrow().getBalance()).isEqualByComparingTo("100.00");
        assertThat(wallets.findById(recipient.walletId()).orElseThrow().getBalance()).isZero();
        assertThat(history.history(senderEmail, 0, 20).getTotalElements()).isZero();
    }

    private static Stream<Arguments> invalidTransfers() {
        return Stream.of(
            Arguments.of("blank idempotency key", """
                {"toWalletId":RECIPIENT,"amount":1.00,"idempotencyKey":" "}
                """),
            Arguments.of("missing idempotency key", """
                {"toWalletId":RECIPIENT,"amount":1.00}
                """),
            Arguments.of("null amount", """
                {"toWalletId":RECIPIENT,"amount":null,"idempotencyKey":"null-amount"}
                """),
            Arguments.of("missing amount", """
                {"toWalletId":RECIPIENT,"idempotencyKey":"missing-amount"}
                """),
            Arguments.of("null recipient", """
                {"toWalletId":null,"amount":1.00,"idempotencyKey":"null-recipient"}
                """),
            Arguments.of("missing recipient", """
                {"amount":1.00,"idempotencyKey":"missing-recipient"}
                """),
            Arguments.of("idempotency key over 100 characters",
                "{\"toWalletId\":RECIPIENT,\"amount\":1.00,\"idempotencyKey\":\"" + "x".repeat(101) + "\"}")
        );
    }
}
