package com.gateway.pricingservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceDto(String symbol, BigDecimal price, String currency, Instant timestamp) {}
