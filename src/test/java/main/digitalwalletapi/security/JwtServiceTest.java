package main.digitalwalletapi.security;

import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "test-only-secret-at-least-32-bytes-long";
    private final JwtService jwt = new JwtService(SECRET, 3600);

    @Test void rejectsExpiredAndWrongIssuerTokens() {
        assertThatThrownBy(() -> jwt.subject(token("digital-wallet-api", Instant.now().minusSeconds(120))))
            .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.subject(token("another-app", Instant.now().plusSeconds(3600))))
            .isInstanceOf(JwtException.class);
    }

    @Test void rejectsWeakConfiguration() {
        assertThatThrownBy(() -> new JwtService("short", 3600)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtService(SECRET, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private String token(String issuer, Instant expiration) {
        var key = new SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("alice@example.com")
            .issuedAt(Instant.now().minusSeconds(3600)).expiresAt(expiration).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
