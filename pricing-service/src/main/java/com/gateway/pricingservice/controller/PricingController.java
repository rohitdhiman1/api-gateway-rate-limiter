package com.gateway.pricingservice.controller;

import com.gateway.pricingservice.dto.PriceDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class PricingController {

    private static final Map<String, PriceDto> PRICES = Map.of(
            "BTC", new PriceDto("BTC", new BigDecimal("67234.50"), "USD", Instant.now()),
            "ETH", new PriceDto("ETH", new BigDecimal("3456.78"), "USD", Instant.now()),
            "SOL", new PriceDto("SOL", new BigDecimal("142.30"), "USD", Instant.now())
    );

    @GetMapping("/")
    public ResponseEntity<List<PriceDto>> getAllPrices() {
        return ResponseEntity.ok(List.copyOf(PRICES.values()));
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<PriceDto> getPrice(@PathVariable String symbol) {
        PriceDto price = PRICES.get(symbol.toUpperCase());
        if (price == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(price);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"UP\",\"service\":\"pricing-service\"}");
    }
}
