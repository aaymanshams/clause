package com.clauseiq.security;

import com.clauseiq.auth.Role;
import com.clauseiq.auth.User;
import com.clauseiq.config.ClauseIqProperties;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "a-test-only-secret-that-is-comfortably-over-32-bytes";

    private static JwtService service(String secret, long minutes) {
        return new JwtService(new ClauseIqProperties(new ClauseIqProperties.Jwt(secret, minutes),
                null, null, null, null, null));
    }

    private static User user() {
        User u = new User(7L, "a@b.test", "hash", Role.USER);
        ReflectionTestUtils.setField(u, "id", 3L);
        return u;
    }

    @Test
    void validTokenRoundTripsTenantAndRole() {
        JwtService jwt = service(SECRET, 60);
        AuthenticatedUser principal = jwt.parse(jwt.generateToken(user())).orElseThrow();
        assertThat(principal.tenantId()).isEqualTo(7L);
        assertThat(principal.userId()).isEqualTo(3L);
        assertThat(principal.role()).isEqualTo(Role.USER);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = service("another-secret-that-is-also-more-than-32-bytes-long", 60).generateToken(user());
        assertThat(service(SECRET, 60).parse(foreign)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = Jwts.builder().subject("3").claim("tenantId", 7L).claim("role", "USER")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(JwtService.signingKey(SECRET)).compact();
        assertThat(service(SECRET, 60).parse(expired)).isEmpty();
    }

    @Test
    void missingSecretUsesARandomKeySoTokensCannotBeForged() {
        JwtService first = service("", 60);
        JwtService second = service(null, 60);
        assertThat(first.parse(first.generateToken(user()))).isPresent();
        assertThat(second.parse(first.generateToken(user()))).isEmpty();
    }

    @Test
    void shortOrPlaceholderSecretsPreventStartup() {
        assertThatThrownBy(() -> service("too-short", 60)).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> service("change-me-to-a-long-random-secret-at-least-32-bytes", 60))
                .hasMessageContaining("placeholder");
    }
}
