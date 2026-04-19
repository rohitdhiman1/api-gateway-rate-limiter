package com.gateway.apigateway.service;

import com.gateway.apigateway.entity.ApiClient;
import com.gateway.apigateway.entity.ClientTier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "test-secret-key-that-is-at-least-32-bytes-long-for-hmac",
                Duration.ofHours(1)
        );
    }

    @Test
    void generateAndValidateToken() {
        ApiClient client = createClient("test-key", ClientTier.FREE);

        String token = jwtService.generateToken(client);
        Claims claims = jwtService.validateToken(token);

        assertThat(claims.getSubject()).isEqualTo("test-key");
        assertThat(claims.get("tier", String.class)).isEqualTo("FREE");
        assertThat(claims.get("clientId", String.class)).isEqualTo(client.getId().toString());
    }

    @Test
    void isTokenValid_withValidToken_returnsTrue() {
        ApiClient client = createClient("key", ClientTier.PREMIUM);
        String token = jwtService.generateToken(client);

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_withInvalidToken_returnsFalse() {
        assertThat(jwtService.isTokenValid("invalid.token.here")).isFalse();
    }

    @Test
    void validateToken_withExpiredToken_throwsException() {
        JwtService shortLivedService = new JwtService(
                "test-secret-key-that-is-at-least-32-bytes-long-for-hmac",
                Duration.ofMillis(1)
        );
        ApiClient client = createClient("key", ClientTier.FREE);
        String token = shortLivedService.generateToken(client);

        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertThatThrownBy(() -> shortLivedService.validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void differentSecrets_cannotValidate() {
        JwtService otherService = new JwtService(
                "different-secret-key-also-at-least-32-bytes-long!!",
                Duration.ofHours(1)
        );
        ApiClient client = createClient("key", ClientTier.FREE);
        String token = jwtService.generateToken(client);

        assertThat(otherService.isTokenValid(token)).isFalse();
    }

    private ApiClient createClient(String apiKey, ClientTier tier) {
        ApiClient client = new ApiClient();
        client.setId(UUID.randomUUID());
        client.setApiKey(apiKey);
        client.setName("Test Client");
        client.setEmail("test@example.com");
        client.setTier(tier);
        return client;
    }
}
