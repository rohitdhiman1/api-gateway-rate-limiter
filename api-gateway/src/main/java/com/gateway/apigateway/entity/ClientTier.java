package com.gateway.apigateway.entity;

public enum ClientTier {
    FREE(100),
    PREMIUM(1000);

    private final int requestsPerMinute;

    ClientTier(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }
}
