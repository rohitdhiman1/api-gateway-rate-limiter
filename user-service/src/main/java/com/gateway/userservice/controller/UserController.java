package com.gateway.userservice.controller;

import com.gateway.userservice.dto.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserController {

    private static final List<UserDto> USERS = List.of(
            new UserDto("USR-001", "Alice Johnson", "alice@bank.com", "Trading", "Senior Trader"),
            new UserDto("USR-002", "Bob Smith", "bob@bank.com", "Engineering", "Platform Engineer"),
            new UserDto("USR-003", "Carol Williams", "carol@bank.com", "Compliance", "Risk Analyst")
    );

    @GetMapping("/")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(USERS);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable String id) {
        return USERS.stream()
                .filter(u -> u.id().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"UP\",\"service\":\"user-service\"}");
    }
}
