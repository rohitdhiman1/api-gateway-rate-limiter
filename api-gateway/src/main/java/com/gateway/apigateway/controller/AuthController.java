package com.gateway.apigateway.controller;

import com.gateway.apigateway.dto.RegisterRequest;
import com.gateway.apigateway.dto.RegisterResponse;
import com.gateway.apigateway.dto.TokenRequest;
import com.gateway.apigateway.dto.TokenResponse;
import com.gateway.apigateway.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "API client registration and token management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new API client", description = "Creates a new API client and returns API key and secret")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/token")
    @Operation(summary = "Get access token", description = "Exchange API key and secret for a JWT access token")
    public ResponseEntity<TokenResponse> token(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }
}
