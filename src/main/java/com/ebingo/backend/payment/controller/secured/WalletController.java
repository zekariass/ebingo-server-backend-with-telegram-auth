/// /package com.ebingo.backend.payment.controller.secured;
/// /
/// /import com.ebingo.backend.common.telegram.TelegramAuthVerifier;
/// /import com.ebingo.backend.common.dto.ApiResponse;
/// /import com.ebingo.backend.payment.dto.WalletDto;
/// /import com.ebingo.backend.payment.service.WalletService;
/// /import com.fasterxml.jackson.core.JsonProcessingException;
/// /import com.fasterxml.jackson.databind.ObjectMapper;
/// /import io.swagger.v3.oas.annotations.Operation;
/// /import io.swagger.v3.oas.annotations.tags.Tag;
/// /import lombok.RequiredArgsConstructor;
/// /import org.springframework.http.HttpStatus;
/// /import org.springframework.http.ResponseEntity;
/// /import org.springframework.web.bind.annotation.GetMapping;
/// /import org.springframework.web.bind.annotation.RequestHeader;
/// /import org.springframework.web.bind.annotation.RequestMapping;
/// /import org.springframework.web.bind.annotation.RestController;
/// /import org.springframework.web.server.ServerWebExchange;
/// /import reactor.core.publisher.Mono;
/// /
/// /import java.time.Instant;
/// /import java.util.Map;
/// /import java.util.Optional;
/// /
/// /@RestController
/// /@Tag(name = "Wallet Secured Controller", description = "Wallet Secured Controller")
/// /@RequestMapping("/api/v1/secured/wallet")
/// /@RequiredArgsConstructor
/// /public class WalletController {
/// /    private final WalletService walletService;
/// /    private final TelegramAuthVerifier telegramAuthVerifier;
/// /    private final ObjectMapper objectMapper;
/// /
/// /    @GetMapping
/// /    @Operation(summary = "Get wallet", description = "Get wallet")
/// /    public Mono<ResponseEntity<ApiResponse<WalletDto>>> getWallet(
/// /            @RequestHeader(value = "x-init-data", required = true) String telegramInitData,
/// /            ServerWebExchange exchange
/// /    ) {
/// /
/// /        Optional<Map<String, String>> initData = telegramAuthVerifier.verifyInitData(telegramInitData);
/// /
/// /        if (initData.isEmpty()) {
/// /            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
/// /                    ApiResponse.<WalletDto>builder()
/// /                            .statusCode(HttpStatus.UNAUTHORIZED.value())
/// /                            .success(false)
/// /                            .message("Invalid telegram init data")
/// /                            .path(exchange.getRequest().getPath().value())
/// /                            .timestamp(Instant.now())
/// /                            .build()
/// /            ));
/// /        }
/// /
/// /        Map<String, Object> user;
/// /
/// /        try {
/// /            user = objectMapper.readValue(initData.get().get("user"), Map.class);
/// /        } catch (JsonProcessingException e) {
/// /            throw new RuntimeException(e);
/// /        }
/// /
/// /        return walletService.getWalletByTelegramId(Long.parseLong(user.get("id").toString()))
/// /                .map(txn -> ApiResponse.<WalletDto>builder()
/// /                        .statusCode(HttpStatus.OK.value())
/// /                        .success(true)
/// /                        .message("Transaction is retrieved successfully")
/// /                        .path(exchange.getRequest().getPath().value())
/// /                        .timestamp(Instant.now())
/// /                        .data(txn)
/// /                        .build()
/// /                )
/// /                .map(ResponseEntity::ok);
/// /    }
/// /
/// /}
//
//
//package com.ebingo.backend.payment.controller.secured;
//
//import com.ebingo.backend.common.telegram.TelegramAuthVerifier;
//import com.ebingo.backend.common.dto.ApiResponse;
//import com.ebingo.backend.payment.dto.WalletDto;
//import com.ebingo.backend.payment.service.WalletService;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestHeader;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Mono;
//
//import java.time.Instant;
//import java.util.Map;
//import java.util.Optional;
//
//@Slf4j
//@RestController
//@RequestMapping("/api/v1/secured/wallet")
//@RequiredArgsConstructor
//@Tag(name = "Wallet Secured Controller", description = "Wallet Secured Controller")
//public class WalletController {
//
//    private final WalletService walletService;
//    private final TelegramAuthVerifier telegramAuthVerifier;
//    private final ObjectMapper objectMapper;
//
//    @GetMapping
//    @Operation(summary = "Get wallet", description = "Get wallet for the authenticated Telegram user")
//    public Mono<ResponseEntity<ApiResponse<WalletDto>>> getWallet(
//            @RequestHeader(value = "x-init-data") String telegramInitData,
//            ServerWebExchange exchange
//    ) {
//
//        Optional<Map<String, String>> initData = telegramAuthVerifier.verifyInitData(telegramInitData);
//
//        if (initData.isEmpty()) {
//            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
//                    ApiResponse.<WalletDto>builder()
//                            .statusCode(HttpStatus.UNAUTHORIZED.value())
//                            .success(false)
//                            .message("Invalid Telegram init data")
//                            .path(exchange.getRequest().getPath().value())
//                            .timestamp(Instant.now())
//                            .build()
//            ));
//        }
//
//        // Extract Telegram user info
//        Map<String, Object> user;
//        try {
//            String userJson = initData.get().get("user");
//            if (userJson == null) {
//                return Mono.just(ResponseEntity.badRequest().body(
//                        ApiResponse.<WalletDto>builder()
//                                .statusCode(HttpStatus.BAD_REQUEST.value())
//                                .success(false)
//                                .message("Missing 'user' field in initData")
//                                .path(exchange.getRequest().getPath().value())
//                                .timestamp(Instant.now())
//                                .build()
//                ));
//            }
//            user = objectMapper.readValue(userJson, Map.class);
//        } catch (JsonProcessingException e) {
//            log.error("Failed to parse Telegram user JSON: {}", e.getMessage());
//            return Mono.just(ResponseEntity.badRequest().body(
//                    ApiResponse.<WalletDto>builder()
//                            .statusCode(HttpStatus.BAD_REQUEST.value())
//                            .success(false)
//                            .message("Invalid user data: " + e.getOriginalMessage())
//                            .path(exchange.getRequest().getPath().value())
//                            .timestamp(Instant.now())
//                            .build()
//            ));
//        }
//
//        // Extract Telegram ID safely
//        Object idObj = user.get("id");
//        if (idObj == null) {
//            return Mono.just(ResponseEntity.badRequest().body(
//                    ApiResponse.<WalletDto>builder()
//                            .statusCode(HttpStatus.BAD_REQUEST.value())
//                            .success(false)
//                            .message("Missing user ID in initData")
//                            .path(exchange.getRequest().getPath().value())
//                            .timestamp(Instant.now())
//                            .build()
//            ));
//        }
//
//        long telegramId;
//        try {
//            if (idObj instanceof Number n) {
//                telegramId = n.longValue();
//            } else {
//                telegramId = Long.parseLong(idObj.toString());
//            }
//        } catch (Exception e) {
//            return Mono.just(ResponseEntity.badRequest().body(
//                    ApiResponse.<WalletDto>builder()
//                            .statusCode(HttpStatus.BAD_REQUEST.value())
//                            .success(false)
//                            .message("Invalid user ID format: " + e.getMessage())
//                            .path(exchange.getRequest().getPath().value())
//                            .timestamp(Instant.now())
//                            .build()
//            ));
//        }
//
//        // Fetch wallet info reactively
//        return walletService.getWalletByTelegramId(telegramId)
//                .map(wallet -> ApiResponse.<WalletDto>builder()
//                        .statusCode(HttpStatus.OK.value())
//                        .success(true)
//                        .message("Wallet retrieved successfully")
//                        .path(exchange.getRequest().getPath().value())
//                        .timestamp(Instant.now())
//                        .data(wallet)
//                        .build()
//                )
//                .map(ResponseEntity::ok)
//                .onErrorResume(ex -> {
//                    log.error("Failed to fetch wallet for Telegram ID {}: {}", telegramId, ex.getMessage(), ex);
//                    return Mono.just(ResponseEntity.internalServerError().body(
//                            ApiResponse.<WalletDto>builder()
//                                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
//                                    .success(false)
//                                    .message("Internal server error: " + ex.getMessage())
//                                    .path(exchange.getRequest().getPath().value())
//                                    .timestamp(Instant.now())
//                                    .build()
//                    ));
//                });
//    }
//}


package com.ebingo.backend.payment.controller.secured;

import com.ebingo.backend.common.dto.ApiResponse;
import com.ebingo.backend.common.telegram.TelegramAuthService;
import com.ebingo.backend.payment.dto.WalletDto;
import com.ebingo.backend.payment.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/secured/wallet")
@Tag(name = "Wallet Secured Controller", description = "Wallet Secured Controller")
public class WalletController {

    private final WalletService walletService;
    private final TelegramAuthService telegramAuthService;

    @GetMapping
    @Operation(summary = "Get wallet", description = "Get wallet for the authenticated Telegram user")
    public Mono<ResponseEntity<ApiResponse<WalletDto>>> getWallet(
            @RequestHeader("x-init-data") String telegramInitData,
            ServerWebExchange exchange
    ) {
        return telegramAuthService.verifyAndExtractUser(telegramInitData)
                .flatMap(user ->
                        walletService.getWalletByTelegramId(user.id())
                                .map(wallet -> ResponseEntity.ok(
                                        ApiResponse.<WalletDto>builder()
                                                .statusCode(HttpStatus.OK.value())
                                                .success(true)
                                                .message("Wallet retrieved successfully")
                                                .data(wallet)
                                                .path(exchange.getRequest().getPath().value())
                                                .timestamp(Instant.now())
                                                .build()
                                ))
                )
                .onErrorResume(ResponseStatusException.class, ex ->
                        Mono.just(ResponseEntity.status(ex.getStatusCode()).body(
                                ApiResponse.<WalletDto>builder()
                                        .statusCode(ex.getStatusCode().value())
                                        .success(false)
                                        .message(ex.getReason())
                                        .path(exchange.getRequest().getPath().value())
                                        .timestamp(Instant.now())
                                        .build()
                        ))
                );
    }
}
