package com.gateway.apigateway.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
        @NotBlank(message = "API key is required")
        String apiKey,

        @NotBlank(message = "API secret is required")
        String apiSecret
) {}
