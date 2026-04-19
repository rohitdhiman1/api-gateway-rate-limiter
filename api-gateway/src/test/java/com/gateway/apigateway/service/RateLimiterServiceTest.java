package com.gateway.apigateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimiterServiceTest {

    private StringRedisTemplate redisTemplate;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        rateLimiterService = new RateLimiterService(redisTemplate);
    }

    @Test
    void checkRateLimit_withinLimit_returnsAllowed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(),
                any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(5L);

        RateLimiterService.RateLimitResult result = rateLimiterService.checkRateLimit("client-1", 100);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(100);
        assertThat(result.remaining()).isEqualTo(95);
    }

    @Test
    void checkRateLimit_exceededLimit_returnsDenied() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(),
                any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(-1L);
        when(redisTemplate.opsForZSet()).thenReturn(mock(org.springframework.data.redis.core.ZSetOperations.class));

        RateLimiterService.RateLimitResult result = rateLimiterService.checkRateLimit("client-1", 100);

        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(100);
    }

    @Test
    void checkRateLimit_differentClients_independentLimits() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("rate_limit:client-1")),
                any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(-1L);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("rate_limit:client-2")),
                any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(1L);
        when(redisTemplate.opsForZSet()).thenReturn(mock(org.springframework.data.redis.core.ZSetOperations.class));

        RateLimiterService.RateLimitResult result1 = rateLimiterService.checkRateLimit("client-1", 100);
        RateLimiterService.RateLimitResult result2 = rateLimiterService.checkRateLimit("client-2", 100);

        assertThat(result1.allowed()).isFalse();
        assertThat(result2.allowed()).isTrue();
    }
}
