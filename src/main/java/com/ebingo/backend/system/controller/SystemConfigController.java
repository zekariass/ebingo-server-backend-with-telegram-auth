package com.ebingo.backend.system.controller;

import com.ebingo.backend.common.dto.ApiResponse;
import com.ebingo.backend.system.dto.SystemConfigDto;
import com.ebingo.backend.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/system-configs")
@RequiredArgsConstructor
@Tag(name = "System Config Endpoints", description = "Endpoints for managing system configurations")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    @Operation(summary = "Get System Configurations", description = "Retrieve system configuration settings")
    public Mono<ResponseEntity<ApiResponse<List<SystemConfigDto>>>> getSystemConfigs(
            ServerWebExchange exchange
    ) {

        return systemConfigService.getAllSystemConfigs()
                .collectList()
                .map(configs -> ResponseEntity.ok(
                        ApiResponse.<List<SystemConfigDto>>builder()
                                .statusCode(HttpStatus.OK.value())
                                .success(true)
                                .message("System configurations are retrieved successfully")
                                .path(exchange.getRequest().getPath().value())
                                .timestamp(Instant.now())
                                .data(configs)
                                .build()
                ));
    }
}
