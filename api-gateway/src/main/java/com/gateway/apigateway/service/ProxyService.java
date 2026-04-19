package com.gateway.apigateway.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final Map<String, String> routeMap;

    public ProxyService(
            WebClient.Builder webClientBuilder,
            @Value("${gateway.routes.user-service-url}") String userServiceUrl,
            @Value("${gateway.routes.pricing-service-url}") String pricingServiceUrl,
            @Value("${gateway.routes.notification-service-url}") String notificationServiceUrl) {
        this.webClient = webClientBuilder.build();
        this.routeMap = Map.of(
                "users", userServiceUrl,
                "pricing", pricingServiceUrl,
                "notifications", notificationServiceUrl
        );
    }

    public String resolveTarget(String path) {
        String stripped = path.replaceFirst("^/api/", "");
        String serviceKey = stripped.contains("/") ? stripped.substring(0, stripped.indexOf('/')) : stripped;
        return routeMap.get(serviceKey);
    }

    public String buildDownstreamPath(String originalPath) {
        return originalPath.replaceFirst("^/api/[^/]+", "");
    }

    @CircuitBreaker(name = "downstreamService", fallbackMethod = "fallback")
    public ResponseEntity<String> forward(HttpServletRequest request, String targetBaseUrl, String downstreamPath) {
        String url = targetBaseUrl + downstreamPath;
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        log.debug("Proxying {} {} -> {}", method, request.getRequestURI(), url);

        WebClient.RequestBodySpec spec = webClient.method(method)
                .uri(url)
                .headers(headers -> copyHeaders(request, headers));

        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId != null) {
            spec.header("X-Correlation-ID", correlationId);
        }

        return spec.exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> ResponseEntity.status(response.statusCode())
                                .headers(h -> response.headers().asHttpHeaders().forEach((k, v) -> {
                                    if (!k.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)) {
                                        h.addAll(k, v);
                                    }
                                }))
                                .body(body)))
                .timeout(TIMEOUT)
                .block();
    }

    public ResponseEntity<String> fallback(HttpServletRequest request, String targetBaseUrl,
                                           String downstreamPath, Throwable throwable) {
        log.error("Circuit breaker tripped for {} {}: {}", request.getMethod(),
                targetBaseUrl + downstreamPath, throwable.getMessage());
        return ResponseEntity.status(503)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("""
                        {"status":503,"error":"Service Unavailable","message":"Downstream service is temporarily unavailable. Please try again later."}""");
    }

    private void copyHeaders(HttpServletRequest request, HttpHeaders headers) {
        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!name.equalsIgnoreCase(HttpHeaders.HOST)
                    && !name.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
                headers.add(name, request.getHeader(name));
            }
        }
    }
}
