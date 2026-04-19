package com.gateway.apigateway.controller;

import com.gateway.apigateway.dto.ErrorResponse;
import com.gateway.apigateway.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayController {

    private final ProxyService proxyService;

    public GatewayController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping("/api/**")
    public ResponseEntity<?> proxy(HttpServletRequest request) {
        String path = request.getRequestURI();
        String targetBaseUrl = proxyService.resolveTarget(path);

        if (targetBaseUrl == null) {
            return ResponseEntity.status(404)
                    .body(new ErrorResponse(404, "Not Found",
                            "No route found for path: " + path, path));
        }

        String downstreamPath = proxyService.buildDownstreamPath(path);
        return proxyService.forward(request, targetBaseUrl, downstreamPath);
    }
}
