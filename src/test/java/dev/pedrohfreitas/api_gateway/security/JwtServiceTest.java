package dev.pedrohfreitas.api_gateway.security;

import dev.pedrohfreitas.api_gateway.exception.JwtValidationException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    // Must be at least 256 bits (32 bytes) for HS256
    private static final String SECRET = "this-is-a-32-byte-secret-key!!";
    private JwtService jwtService;
    private SecretKey signingKey;
    private String validToken;

    @BeforeEach
    void setUp() throws Exception {
        // Create JwtService and inject secret via reflection
        jwtService = new JwtService();
        var secretField = JwtService.class.getDeclaredField("secret");
        secretField.setAccessible(true);
        secretField.set(jwtService, SECRET);
        jwtService.init(); // initialize signing key

        byte[] keyBytes = SECRET.getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[32];
        System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
        signingKey = Keys.hmacShaKeyFor(padded);

        validToken = Jwts.builder()
                .subject("user123")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(signingKey)
                .compact();
    }

    @Test
    @DisplayName("Should validate a valid token and return claims")
    void shouldValidateValidToken() {
        var claims = jwtService.validateToken(validToken);
        assertThat(claims.getSubject()).isEqualTo("user123");
    }

    @Test
    @DisplayName("Should validate token with Bearer prefix")
    void shouldValidateBearerToken() {
        var claims = jwtService.validateToken("Bearer " + validToken);
        assertThat(claims.getSubject()).isEqualTo("user123");
    }

    @Test
    @DisplayName("Should throw on null token")
    void shouldThrowOnNullToken() {
        assertThatThrownBy(() -> jwtService.validateToken(null))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("Should throw on empty token")
    void shouldThrowOnEmptyToken() {
        assertThatThrownBy(() -> jwtService.validateToken(""))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("Should throw on expired token")
    void shouldThrowOnExpiredToken() {
        String expired = Jwts.builder()
                .subject("user123")
                .issuedAt(new Date(System.currentTimeMillis() - 7200_000))
                .expiration(new Date(System.currentTimeMillis() - 3600_000))
                .signWith(signingKey)
                .compact();

        assertThatThrownBy(() -> jwtService.validateToken(expired))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Should throw on token signed with different key")
    void shouldThrowOnWrongKey() {
        byte[] otherKey = "another-32-byte-secret-key-here!".getBytes(StandardCharsets.UTF_8);
        byte[] padded2 = new byte[32];
        System.arraycopy(otherKey, 0, padded2, 0, Math.min(otherKey.length, 32));
        SecretKey wrongKey = Keys.hmacShaKeyFor(padded2);

        String badToken = Jwts.builder()
                .subject("user123")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(wrongKey)
                .compact();

        assertThatThrownBy(() -> jwtService.validateToken(badToken))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("Should throw on missing Authorization header")
    void shouldThrowOnMissingAuthHeader() {
        assertThatThrownBy(() -> jwtService.validateFromAuthorizationHeader(null))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Authorization");
    }
}
