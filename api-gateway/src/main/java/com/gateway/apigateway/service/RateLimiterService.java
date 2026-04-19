package com.gateway.apigateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final Duration WINDOW_SIZE = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> slidingWindowScript;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowScript = RedisScript.of("""
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local window = tonumber(ARGV[2])
                local limit = tonumber(ARGV[3])
                local member = ARGV[4]

                -- Remove entries outside the sliding window
                redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

                -- Count current entries in window
                local count = redis.call('ZCARD', key)

                if count < limit then
                    -- Add this request
                    redis.call('ZADD', key, now, member)
                    redis.call('PEXPIRE', key, window)
                    return count + 1
                else
                    return -1
                end
                """, Long.class);
    }

    public RateLimitResult checkRateLimit(String clientId, int maxRequests) {
        String key = "rate_limit:" + clientId;
        long nowMillis = System.currentTimeMillis();
        String member = nowMillis + ":" + Thread.currentThread().getId();

        Long result = redisTemplate.execute(
                slidingWindowScript,
                List.of(key),
                String.valueOf(nowMillis),
                String.valueOf(WINDOW_SIZE.toMillis()),
                String.valueOf(maxRequests),
                member
        );

        if (result == null || result == -1) {
            long remaining = getRemainingRequests(key, nowMillis, maxRequests);
            long retryAfterSeconds = getRetryAfterSeconds(key, nowMillis);
            log.warn("Rate limit exceeded for client={}, limit={}", clientId, maxRequests);
            return new RateLimitResult(false, 0, maxRequests, retryAfterSeconds);
        }

        int currentCount = result.intValue();
        return new RateLimitResult(true, maxRequests - currentCount, maxRequests, 0);
    }

    private long getRemainingRequests(String key, long nowMillis, int maxRequests) {
        Long count = redisTemplate.opsForZSet().count(key,
                nowMillis - WINDOW_SIZE.toMillis(), nowMillis);
        return count == null ? 0 : Math.max(0, maxRequests - count);
    }

    private long getRetryAfterSeconds(String key, long nowMillis) {
        var entries = redisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
        if (entries == null || entries.isEmpty()) {
            return WINDOW_SIZE.toSeconds();
        }
        double oldestScore = entries.iterator().next().getScore();
        long resetTime = (long) oldestScore + WINDOW_SIZE.toMillis();
        return Math.max(1, (resetTime - nowMillis) / 1000);
    }

    public record RateLimitResult(
            boolean allowed,
            long remaining,
            int limit,
            long retryAfterSeconds
    ) {}
}
