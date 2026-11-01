package main.digitalwalletapi.regression;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import main.digitalwalletapi.model.User;
import main.digitalwalletapi.model.Wallet;
import main.digitalwalletapi.repository.WalletRepository;
import main.digitalwalletapi.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletDependencyRegressionTest {
    @Test
    @DisplayName("Issue 13: a wallet service can be constructed with its required repository outside Spring")
    void requiredRepositoryIsExplicitAtConstruction() throws Exception {
        Constructor<?> constructor = Arrays.stream(WalletService.class.getConstructors())
            .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{WalletRepository.class}))
            .findFirst().orElse(null);
        assertThat(constructor).as("constructor accepting the required WalletRepository").isNotNull();
        WalletRepository repository = mock(WalletRepository.class);
        Wallet wallet = new Wallet(new User("Alice", "alice@example.com", "hash"));
        when(repository.findByUserEmail("alice@example.com")).thenReturn(Optional.of(wallet));
        WalletService service = (WalletService) constructor.newInstance(repository);
        assertThat(service.requireWallet("alice@example.com")).isSameAs(wallet);
    }

    @Test
    @DisplayName("Issue 13: the required wallet repository cannot be reassigned after construction")
    void requiredRepositoryIsFinal() {
        var repositories = Arrays.stream(WalletService.class.getDeclaredFields())
            .filter(field -> field.getType().equals(WalletRepository.class)).toList();
        assertThat(repositories).hasSize(1);
        assertThat(Modifier.isFinal(repositories.getFirst().getModifiers()))
            .as("required repository is immutable").isTrue();
    }
}
