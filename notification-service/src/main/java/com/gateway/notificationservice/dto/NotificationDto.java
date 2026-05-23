package com.gateway.notificationservice.dto;

import java.time.Instant;

public record NotificationDto(String id, String type, String message, String severity, Instant createdAt) {}
