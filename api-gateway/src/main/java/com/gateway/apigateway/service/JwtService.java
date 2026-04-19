package com.gateway.apigateway.service;

import com.gateway.apigateway.entity.ApiClient;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration tokenExpiry;

    public JwtService(
            @Value("${gateway.jwt.secret}") String secret,
            @Value("${gateway.jwt.expiry:PT1H}") Duration tokenExpiry) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenExpiry = tokenExpiry;
    }

    public String generateToken(ApiClient client) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(client.getApiKey())
                .claim("clientId", client.getId().toString())
                .claim("tier", client.getTier().name())
                .claim("name", client.getName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenExpiry)))
                .signWith(signingKey)
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public long getExpirySeconds() {
        return tokenExpiry.toSeconds();
    }
}
