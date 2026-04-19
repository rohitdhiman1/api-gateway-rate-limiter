package com.gateway.apigateway.service;

import com.gateway.apigateway.dto.RegisterRequest;
import com.gateway.apigateway.dto.RegisterResponse;
import com.gateway.apigateway.dto.TokenRequest;
import com.gateway.apigateway.dto.TokenResponse;
import com.gateway.apigateway.entity.ApiClient;
import com.gateway.apigateway.entity.ClientTier;
import com.gateway.apigateway.repository.ApiClientRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AuthService {

    private final ApiClientRepository clientRepository;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(ApiClientRepository clientRepository, JwtService jwtService) {
        this.clientRepository = clientRepository;
        this.jwtService = jwtService;
    }

    public RegisterResponse register(RegisterRequest request) {
        if (clientRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }

        String apiKey = generateSecureToken(24);
        String apiSecret = generateSecureToken(32);

        ApiClient client = new ApiClient();
        client.setName(request.name());
        client.setEmail(request.email());
        client.setApiKey(apiKey);
        client.setApiSecret(apiSecret);
        client.setTier(ClientTier.valueOf(request.tier()));

        clientRepository.save(client);

        return new RegisterResponse(
                apiKey,
                apiSecret,
                client.getTier().name(),
                "Registration successful. Store your API key and secret securely — the secret cannot be retrieved again."
        );
    }

    public TokenResponse authenticate(TokenRequest request) {
        ApiClient client = clientRepository.findByApiKey(request.apiKey())
                .orElseThrow(() -> new SecurityException("Invalid API credentials"));

        if (!client.getApiSecret().equals(request.apiSecret())) {
            throw new SecurityException("Invalid API credentials");
        }

        if (!client.isEnabled()) {
            throw new SecurityException("Client account is disabled");
        }

        String token = jwtService.generateToken(client);
        return new TokenResponse(token, jwtService.getExpirySeconds());
    }

    private String generateSecureToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
