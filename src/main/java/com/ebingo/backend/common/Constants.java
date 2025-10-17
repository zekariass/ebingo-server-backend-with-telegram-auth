package com.ebingo.backend.common;

import java.util.List;

public final class Constants {
    public static final List<String> PUBLIC_API_PATHS_FOR_FILTER = List.of(
            "/api/auth",
            "/swagger-ui",
            "/v3/api-docs",
            "/swagger-resource",
            "/webjars",
            "/docs",
            "/api/v1/secured/billing/payment/chapa/webhook",
            "/api/v1/secured/billing/payment/pesapal/IPN"
    );

    public static final List<String> PUBLIC_API_ENDPOINTS_FOR_ACCESS = List.of(
            "/",
            "/api/v1/users",
            "/api/v1/users/**",
            "/api/v1/public/common/languages/**",
            "/api/auth/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-resource/**",
            "/webjars/**",
            "/docs",
            "/api/v1/secured/billing/payment/chapa/webhook",
            "/api/v1/secured/billing/payment/pesapal/IPN",
            "ws://localhost:8080/ws/game",
            "/actuator/**"
    );
}
