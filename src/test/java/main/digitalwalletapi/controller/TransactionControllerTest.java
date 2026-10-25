package main.digitalwalletapi.controller;
import main.digitalwalletapi.dto.*;
import main.digitalwalletapi.service.AuthService;
import main.digitalwalletapi.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired main.digitalwalletapi.repository.WalletRepository wallets;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Test void protectedEndpointsRejectAnonymousAndInvalidTokens() throws Exception {
        mvc.perform(get("/api/wallet")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/transaction")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/transaction/transfer").contentType("application/json").content("""
{}
"""))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/wallet").header("Authorization", "Bearer broken.token"))
            .andExpect(status().isUnauthorized());
    }
    @Test void registerLoginAndReadBalanceThroughHttp() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("""
{"name":"Alice","email":"%s","password":"password123"}
""" .formatted(email)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.token").isString());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("""
{"email":"%s","password":"wrong"}
""" .formatted(email)))
            .andExpect(status().isUnauthorized());
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("""
{"email":"%s","password":"password123"}
""" .formatted(email)))
            .andExpect(status().isOk()).andReturn();
        String token = tools.jackson.databind.json.JsonMapper.builder().build()
            .readTree(result.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0));
    }
    @Test void validatesRequestsAndHistoryBounds() throws Exception {
        var user = auth.register(new RegisterRequest("Alice", UUID.randomUUID() + "@example.com", "password123"));
        String bearer = "Bearer " + user.token();
        mvc.perform(post("/api/transaction/transfer").header("Authorization", bearer)
                .contentType("application/json").content("""
{"toWalletId":1,"amount":-1,"idempotencyKey":"key"}
"""))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/transaction?size=101").header("Authorization", bearer)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("""
{"name":"Alice","email":"invalid","password":"short"}
"""))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " +
                new JwtService("different-test-secret-with-32-bytes", 3600).generate("alice@example.com")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " +
                new JwtService("test-only-secret-at-least-32-bytes-long", 3600).generate("deleted@example.com")))
            .andExpect(status().isUnauthorized());
    }

    @Test void transferAndHistoryUseAuthenticatedWallet() throws Exception {
        var sender = auth.register(new RegisterRequest("Sender", UUID.randomUUID() + "@example.com", "password123"));
        var recipient = auth.register(new RegisterRequest("Recipient", UUID.randomUUID() + "@example.com", "password123"));
        var stranger = auth.register(new RegisterRequest("Stranger", UUID.randomUUID() + "@example.com", "password123"));
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status ->
            wallets.findLockedById(sender.walletId()).orElseThrow().credit(new java.math.BigDecimal("50.00")));
        String request = """
            {"toWalletId":%d,"amount":12.50,"idempotencyKey":"http-payment"}
            """.formatted(recipient.walletId());
        var response = mvc.perform(post("/api/transaction/transfer").header("Authorization", "Bearer " + sender.token())
                .contentType("application/json").content(request))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fromWalletId").value(sender.walletId()))
            .andExpect(jsonPath("$.toWalletId").value(recipient.walletId()))
            .andExpect(jsonPath("$.amount").value(12.5)).andReturn();
        long id = tools.jackson.databind.json.JsonMapper.builder().build()
            .readTree(response.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/transaction/transfer").header("Authorization", "Bearer " + sender.token())
                .contentType("application/json").content(request))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(post("/api/transaction/transfer").header("Authorization", "Bearer " + sender.token())
                .contentType("application/json").content(request.replace("12.50", "13.50")))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + sender.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(37.5));
        mvc.perform(get("/api/transaction").header("Authorization", "Bearer " + recipient.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get("/api/transaction").header("Authorization", "Bearer " + stranger.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
}
