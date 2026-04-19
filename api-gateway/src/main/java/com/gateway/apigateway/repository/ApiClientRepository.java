package com.gateway.apigateway.repository;

import com.gateway.apigateway.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {

    Optional<ApiClient> findByApiKey(String apiKey);

    Optional<ApiClient> findByEmail(String email);

    boolean existsByEmail(String email);
}
