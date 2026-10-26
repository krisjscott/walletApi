package main.digitalwalletapi.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String ISSUER = "digital-wallet-api";
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long expirationSeconds;

    public JwtService( @Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-seconds:3600}") long expirationSeconds ) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32 || expirationSeconds <= 0)
            throw new IllegalArgumentException("JWT secret needs at least 32 bytes and expiration must be positive");
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        nimbus.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        decoder = nimbus;
        this.expirationSeconds = expirationSeconds;
    }

    public String generate( String email ) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(email).issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public String subject( String token ) {
        return decoder.decode(token).getSubject();
    }

    public long expiresIn() {
        return expirationSeconds;
    }
}
