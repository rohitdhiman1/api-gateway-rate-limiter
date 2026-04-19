package com.gateway.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.apigateway.dto.ErrorResponse;
import com.gateway.apigateway.entity.ClientTier;
import com.gateway.apigateway.service.RateLimiterService;
import com.gateway.apigateway.service.RateLimiterService.RateLimitResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(3)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/auth/register", "/auth/token",
            "/actuator", "/swagger-ui", "/v3/api-docs"
    );

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (EXCLUDED_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = (String) request.getAttribute("clientId");
        String tierStr = (String) request.getAttribute("clientTier");

        if (clientId == null || tierStr == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ClientTier tier = ClientTier.valueOf(tierStr);
        RateLimitResult result = rateLimiterService.checkRateLimit(clientId, tier.getRequestsPerMinute());

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (!result.allowed()) {
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse error = new ErrorResponse(429, "Too Many Requests",
                    "Rate limit exceeded. Try again in " + result.retryAfterSeconds() + " seconds.",
                    request.getRequestURI());
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
