package com.gateway.apigateway.dto;

public record RegisterResponse(
        String apiKey,
        String apiSecret,
        String tier,
        String message
) {}
