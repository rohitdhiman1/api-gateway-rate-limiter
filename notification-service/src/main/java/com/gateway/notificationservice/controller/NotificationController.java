package com.gateway.notificationservice.controller;

import com.gateway.notificationservice.dto.NotificationDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class NotificationController {

    private static final List<NotificationDto> NOTIFICATIONS = List.of(
            new NotificationDto("NTF-001", "TRADE", "BTC buy order executed at $67,234.50",
                    "INFO", Instant.now()),
            new NotificationDto("NTF-002", "ALERT", "ETH price dropped below $3,400 threshold",
                    "WARNING", Instant.now()),
            new NotificationDto("NTF-003", "SYSTEM", "Scheduled maintenance window: 2024-03-15 02:00 UTC",
                    "INFO", Instant.now())
    );

    @GetMapping("/")
    public ResponseEntity<List<NotificationDto>> getAllNotifications() {
        return ResponseEntity.ok(NOTIFICATIONS);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationDto> getNotification(@PathVariable String id) {
        return NOTIFICATIONS.stream()
                .filter(n -> n.id().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"UP\",\"service\":\"notification-service\"}");
    }
}
