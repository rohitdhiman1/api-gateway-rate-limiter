package com.gateway.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gateway.apigateway.entity.ApiClient;
import com.gateway.apigateway.entity.ClientTier;
import com.gateway.apigateway.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private JwtService jwtService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "test-secret-key-that-is-at-least-32-bytes-long-for-hmac",
                Duration.ofHours(1)
        );
        filter = new JwtAuthenticationFilter(jwtService, objectMapper);
    }

    @Test
    void publicPaths_passThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void missingAuthHeader_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or invalid Authorization header");
    }

    @Test
    void invalidToken_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/");
        request.addHeader("Authorization", "Bearer invalid.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid or expired token");
    }

    @Test
    void validToken_setsAttributesAndContinues() throws Exception {
        ApiClient client = new ApiClient();
        client.setId(UUID.randomUUID());
        client.setApiKey("test-key");
        client.setName("Test");
        client.setEmail("test@example.com");
        client.setTier(ClientTier.PREMIUM);

        String token = jwtService.generateToken(client);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("clientId")).isEqualTo(client.getId().toString());
        assertThat(request.getAttribute("clientTier")).isEqualTo("PREMIUM");
    }
}
