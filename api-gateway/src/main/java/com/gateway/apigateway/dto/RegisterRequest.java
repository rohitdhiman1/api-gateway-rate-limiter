package com.gateway.apigateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Pattern(regexp = "FREE|PREMIUM", message = "Tier must be FREE or PREMIUM")
        String tier
) {
    public RegisterRequest {
        if (tier == null || tier.isBlank()) {
            tier = "FREE";
        }
    }
}
