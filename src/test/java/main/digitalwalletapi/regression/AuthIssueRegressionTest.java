package main.digitalwalletapi.regression;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import main.digitalwalletapi.dto.AuthResponse;
import main.digitalwalletapi.dto.LoginRequest;
import main.digitalwalletapi.dto.RegisterRequest;
import main.digitalwalletapi.model.User;
import main.digitalwalletapi.model.Wallet;
import main.digitalwalletapi.repository.UserRepository;
import main.digitalwalletapi.repository.WalletRepository;
import main.digitalwalletapi.security.JwtService;
import main.digitalwalletapi.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Isolated("Changes the JVM default locale and restores it after the test")
class AuthIssueRegressionTest {
    private UserRepository users;
    private WalletRepository wallets;
    private AuthService auth;
    private final JwtService jwt = new JwtService("regression-test-secret-at-least-32-bytes", 7200);
    private final AtomicReference<User> savedUser = new AtomicReference<>();
    private final AtomicReference<Wallet> savedWallet = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        wallets = mock(WalletRepository.class);
        when(users.save(any())).thenAnswer(call -> {
            savedUser.set(call.getArgument(0));
            return savedUser.get();
        });
        when(wallets.save(any())).thenAnswer(call -> {
            savedWallet.set(call.getArgument(0));
            return savedWallet.get();
        });
        when(users.findByEmail(any())).thenAnswer(call -> Optional.ofNullable(savedUser.get())
            .filter(user -> user.getEmail().equals(call.getArgument(0))));
        when(wallets.findByUserEmail(any())).thenAnswer(call -> Optional.ofNullable(savedWallet.get())
            .filter(wallet -> wallet.getUser().getEmail().equals(call.getArgument(0))));
        auth = new AuthService(users, wallets, new BCryptPasswordEncoder(), jwt);
    }

    @Test
    @DisplayName("Issue 1: registration and login retain identity across default locales")
    void emailNormalizationIsIndependentOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            AuthResponse registered = auth.register(new RegisterRequest("Iris", "IRIS@example.com", "password123"));
            assertThat(jwt.subject(registered.token())).isEqualTo("iris@example.com");
            Locale.setDefault(Locale.US);
            AuthResponse loggedIn = auth.login(new LoginRequest("IRIS@example.com", "password123"));
            assertThat(jwt.subject(loggedIn.token())).isEqualTo("iris@example.com");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("Issue 2: passwords containing surrounding spaces authenticate exactly as registered")
    void loginPreservesPasswordWhitespace() {
        String password = " password123 ";
        auth.register(new RegisterRequest("Alice", "alice@example.com", password));
        assertThatCode(() -> auth.login(new LoginRequest("alice@example.com", password)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Issue 3: auth responses report token lifetime in seconds")
    void registrationAndLoginReportConfiguredExpiryInSeconds() {
        var registered = auth.register(new RegisterRequest("Alice", "alice@example.com", "password123"));
        var loggedIn = auth.login(new LoginRequest("alice@example.com", "password123"));
        assertThat(registered.expiresIn()).as("registration expiresIn").isEqualTo(jwt.expiresIn());
        assertThat(loggedIn.expiresIn()).as("login expiresIn").isEqualTo(jwt.expiresIn());
    }

    @Test
    @DisplayName("Issue 14: registration uses the supplied password encoder")
    void registrationRespectsConfiguredPasswordEncoder() {
        auth = new AuthService(users, wallets, new TestPasswordEncoder(), jwt);
        auth.register(new RegisterRequest("Alice", "alice@example.com", "password123"));
        assertThat(savedUser.get().getPassword()).isEqualTo("test-encoder:password123");
    }

    @Test
    @DisplayName("Issue 14: login accepts hashes supported by the supplied password encoder")
    void loginRespectsConfiguredPasswordEncoder() {
        PasswordEncoder encoder = new TestPasswordEncoder();
        auth = new AuthService(users, wallets, encoder, jwt);
        User user = new User("Alice", "alice@example.com", encoder.encode("password123"));
        savedUser.set(user);
        savedWallet.set(new Wallet(user));
        assertThatCode(() -> auth.login(new LoginRequest("alice@example.com", "password123")))
            .doesNotThrowAnyException();
    }

    private static class TestPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return "test-encoder:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encode(rawPassword).equals(encodedPassword);
        }
    }
}
