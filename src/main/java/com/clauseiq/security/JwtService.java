package com.clauseiq.security;

import com.clauseiq.auth.Role;
import com.clauseiq.auth.User;
import com.clauseiq.config.ClauseIqProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(ClauseIqProperties properties) {
        this.key = signingKey(properties.jwt().secret());
        this.expiration = Duration.ofMinutes(properties.jwt().expirationMinutes());
    }

    /**
     * There is deliberately no default secret: a secret committed to the repo would let anyone forge a
     * token for any tenant. Without JWT_SECRET a random per-process key is used (tokens stop working on
     * restart), which is safe for local development.
     */
    static SecretKey signingKey(String configured) {
        if (configured == null || configured.isBlank()) {
            log.warn("JWT_SECRET is not set: using a random signing key for this process. Tokens will be "
                    + "invalid after a restart. Set JWT_SECRET (32+ bytes) for any shared deployment.");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return Keys.hmacShaKeyFor(random);
        }
        byte[] secret = configured.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        if (configured.toLowerCase(Locale.ROOT).contains("change-me")) {
            throw new IllegalStateException("JWT_SECRET is still a placeholder value; generate one with "
                    + "`openssl rand -base64 48` or leave it unset for local development");
        }
        return Keys.hmacShaKeyFor(secret);
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("tenantId", user.getTenantId())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public long expiresInSeconds() {
        return expiration.toSeconds();
    }

    /** Returns the principal for a valid token, or empty if the token is invalid/expired/tampered. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("tenantId", Long.class),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
