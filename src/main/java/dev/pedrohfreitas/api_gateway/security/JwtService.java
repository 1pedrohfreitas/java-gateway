package dev.pedrohfreitas.api_gateway.security;

import dev.pedrohfreitas.api_gateway.exception.JwtValidationException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
public class JwtService {

    @Value("${gateway.jwt.secret}")
    private String secret;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        // Ensure the key is at least 256 bits (32 bytes)
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Pad or hash to reach minimum length
            log.warn("JWT secret is shorter than 256 bits. Padding to minimum length.");
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Validate a JWT token and return the claims if valid.
     *
     * @param token the JWT token (with or without "Bearer " prefix)
     * @return the parsed JWS claims
     * @throws JwtValidationException if the token is invalid, expired, or malformed
     */
    public Claims validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtValidationException("Token is missing or empty");
        }

        // Strip "Bearer " prefix if present
        String jwt = token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();

        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new JwtValidationException("Token expired at " + e.getClaims().getExpiration(), e);
        } catch (UnsupportedJwtException e) {
            throw new JwtValidationException("Unsupported JWT format", e);
        } catch (MalformedJwtException e) {
            throw new JwtValidationException("Malformed JWT token", e);
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException("Token is empty or null", e);
        } catch (SignatureException e) {
            throw new JwtValidationException("Invalid JWT signature", e);
        } catch (Exception e) {
            throw new JwtValidationException("JWT validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extract the token from the Authorization header and validate it.
     */
    public Claims validateFromAuthorizationHeader(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new JwtValidationException("Authorization header is missing");
        }
        return validateToken(authHeader);
    }
}
