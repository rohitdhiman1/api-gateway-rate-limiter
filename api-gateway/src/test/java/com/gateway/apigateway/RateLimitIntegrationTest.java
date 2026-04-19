package com.gateway.apigateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RateLimitIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("gateway_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        // Clear rate limit keys
        var keys = redisTemplate.keys("rate_limit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // Use the seeded demo free user (100 req/min limit)
        MvcResult tokenResult = mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"demo-free-api-key\",\"apiSecret\":\"demo-free-api-secret\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode tokenJson = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        accessToken = tokenJson.get("accessToken").asText();
    }

    @Test
    void rateLimiter_allowsRequestsWithinLimit_thenRejects_thenRecovers() throws Exception {
        // Send 100 requests — all should succeed (or get 404 since downstream isn't running, but not 429)
        for (int i = 0; i < 100; i++) {
            mockMvc.perform(get("/api/users/")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status).isNotEqualTo(429);
                    });
        }

        // The 101st request should be rate limited
        mockMvc.perform(get("/api/users/")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> {
                    String retryAfter = result.getResponse().getHeader("Retry-After");
                    assertThat(retryAfter).isNotNull();
                    assertThat(Integer.parseInt(retryAfter)).isGreaterThan(0);
                });

        // Clear rate limit to simulate window reset
        var keys = redisTemplate.keys("rate_limit:*");
        if (keys != null) {
            redisTemplate.delete(keys);
        }

        // After reset, requests should succeed again
        mockMvc.perform(get("/api/users/")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isNotEqualTo(429);
                });
    }

    @Test
    void rateLimiter_returnsCorrectHeaders() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();

        assertThat(result.getResponse().getHeader("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(result.getResponse().getHeader("X-RateLimit-Remaining")).isNotNull();
    }

    @Test
    void authEndpoints_areNotRateLimited() throws Exception {
        for (int i = 0; i < 110; i++) {
            mockMvc.perform(post("/auth/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"apiKey\":\"demo-free-api-key\",\"apiSecret\":\"demo-free-api-secret\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void unauthenticatedRequest_returns401() throws Exception {
        mockMvc.perform(get("/api/users/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerAndAuthenticate_flow() throws Exception {
        // Register
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Integration Test\",\"email\":\"inttest@example.com\",\"tier\":\"PREMIUM\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String apiKey = registerJson.get("apiKey").asText();
        String apiSecret = registerJson.get("apiSecret").asText();

        // Get token
        MvcResult tokenResult = mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + apiKey + "\",\"apiSecret\":\"" + apiSecret + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode tokenJson = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        assertThat(tokenJson.get("accessToken").asText()).isNotBlank();
        assertThat(tokenJson.get("tokenType").asText()).isEqualTo("Bearer");
    }
}
