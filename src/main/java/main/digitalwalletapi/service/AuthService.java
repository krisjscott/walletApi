package main.digitalwalletapi.service;

import lombok.RequiredArgsConstructor;
import main.digitalwalletapi.dto.*;
import main.digitalwalletapi.model.*;
import main.digitalwalletapi.repository.*;
import main.digitalwalletapi.security.JwtService;
import main.digitalwalletapi.exception.IdempotencyConflictException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;


@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final WalletRepository wallets;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.findByEmail(email).isPresent())
            throw new IdempotencyConflictException("Email is already registered");
        // BCrypt limits passwords by UTF-8 bytes, rather than Java characters.
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
        }
        User user = users.save(new User(request.name().trim(),email,passwords.encode(request.password())));
        Wallet wallet = wallets.save(new Wallet(user));
        return response(user,wallet);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email())).orElseThrow(()
                -> new BadCredentialsException("Invalid credentials"));
        if (!passwords.matches(request.password(), user.getPassword()))
            throw new BadCredentialsException("Invalid credentials");
        Wallet wallet = wallets.findByUserEmail(user.getEmail()).orElseThrow(() -> new IllegalStateException("User has no wallet"));
        return response(user, wallet);
    }

    private AuthResponse response(User user,Wallet wallet) {
        return new AuthResponse(jwt.generate(user.getEmail()),"Bearer",
                jwt.expiresIn() ,user.getId(),wallet.getId());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
