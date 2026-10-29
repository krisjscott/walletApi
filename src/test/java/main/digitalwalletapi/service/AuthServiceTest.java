package main.digitalwalletapi.service;

import main.digitalwalletapi.dto.*;
import main.digitalwalletapi.model.*;
import main.digitalwalletapi.repository.*;
import main.digitalwalletapi.security.JwtService;
import main.digitalwalletapi.exception.IdempotencyConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final JwtService jwt = new JwtService("test-secret-with-at-least-32-bytes", 3600);
    private final AuthService auth = new AuthService(users, wallets, passwords, jwt);

    @Test
    void registrationHashesPasswordAndCreatesZeroBalanceWallet() {
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        when(wallets.save(any())).thenAnswer(call -> call.getArgument(0));
        var response = auth.register(new RegisterRequest("Alice", "ALICE@example.com", "password123"));
        var user = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        assertThat(user.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(passwords.matches("password123", user.getValue().getPassword())).isTrue();
        assertThat(user.getValue().getRole()).isEqualTo(Role.USER);
        var wallet = org.mockito.ArgumentCaptor.forClass(Wallet.class);
        verify(wallets).save(wallet.capture());
        assertThat(wallet.getValue().getBalance()).isZero();
        assertThat(jwt.subject(response.token())).isEqualTo("alice@example.com");
    }

    @Test
    void duplicateRegistrationIsRejected() {
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(new User("Alice", "alice@example.com", "hash")));
        assertThatThrownBy(() -> auth.register(new RegisterRequest("Alice", "alice@example.com", "password123")))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(wallets, never()).save(any());
    }

    @Test
    void invalidLoginIsRejected() {
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(new User("Alice", "alice@example.com", passwords.encode("password123"))));
        assertThatThrownBy(() -> auth.login(new LoginRequest("alice@example.com", "wrong"))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> auth.login(new LoginRequest("missing@example.com", "wrong"))).isInstanceOf(BadCredentialsException.class);
    }
}
