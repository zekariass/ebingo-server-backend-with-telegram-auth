//package com.ebingo.backend.payment.controller.secured;
//
//import com.ebingo.backend.common.dto.ApiResponse;
//import com.ebingo.backend.payment.dto.DepositTransferDto;
//import com.ebingo.backend.payment.dto.DepositTransferRequestDto;
//import com.ebingo.backend.payment.service.DepositTransferService;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Mono;
//
//import java.time.Instant;
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/v1/secured/deposit/transfers")
//@RequiredArgsConstructor
//@Tag(name = "Deposit Transfers Endpoint", description = "Deposit Transfers Endpoint")
//public class DepositTransferController {
//
//    private final DepositTransferService depositTransferService;
//
//    @GetMapping
//    @Operation(summary = "Get paginated deposit transfers", description = "Get paginated deposit transfers")
//    public Mono<ResponseEntity<ApiResponse<List<DepositTransferDto>>>> getPaginatedDepositTransfers(
//            @RequestParam Long telegramId,
//            @RequestParam Integer page,
//            @RequestParam(defaultValue = "10") Integer size,
//            @RequestParam String sortBy,
//            ServerWebExchange exchange
//    ) {
//
//        return depositTransferService.getPaginatedDepositTransfer(telegramId, page, size, sortBy)
//                .collectList()
//                .map(transfers -> ApiResponse.<List<DepositTransferDto>>builder()
//                        .statusCode(HttpStatus.OK.value())
//                        .success(true)
//                        .message("Transfers retrieved successfully")
//                        .path(exchange.getRequest().getPath().value())
//                        .timestamp(Instant.now())
//                        .data(transfers)
//                        .build())
//                .map(ResponseEntity::ok);
//    }
//
//
//    @GetMapping("/{id}")
//    @Operation(summary = "Get a single deposit transfer", description = "Get a single deposit transfer")
//    public Mono<ResponseEntity<ApiResponse<DepositTransferDto>>> getASingleDepositTransfer(
//            @RequestParam Long telegramId,
//            @RequestParam Long id,
//            ServerWebExchange exchange
//    ) {
//
//        return depositTransferService.getASingleDepositTransfer(id, telegramId)
//                .map(transfer -> ApiResponse.<DepositTransferDto>builder()
//                        .statusCode(HttpStatus.OK.value())
//                        .success(true)
//                        .message("Transfer retrieved successfully")
//                        .path(exchange.getRequest().getPath().value())
//                        .timestamp(Instant.now())
//                        .data(transfer)
//                        .build())
//                .map(ResponseEntity::ok);
//    }
//
//
//    @PostMapping
//    @Operation(summary = "Create a deposit transfer", description = "Create a deposit transfer")
//    public Mono<ResponseEntity<ApiResponse<DepositTransferDto>>> createDepositTransfer(
//            @RequestParam Long telegramId,
//            @Valid @RequestBody DepositTransferRequestDto depositTransferDto,
//            ServerWebExchange exchange
//    ) {
//
//        return depositTransferService.createDepositTransfer(depositTransferDto, telegramId)
//                .map(transfer -> ApiResponse.<DepositTransferDto>builder()
//                        .statusCode(HttpStatus.CREATED.value())
//                        .success(true)
//                        .message("Transfer created successfully")
//                        .path(exchange.getRequest().getPath().value())
//                        .timestamp(Instant.now())
//                        .data(transfer)
//                        .build())
//                .map(ResponseEntity::ok);
//    }
//
//
//    @DeleteMapping("/{id}")
//    @Operation(summary = "Delete a single deposit transfer", description = "Delete a single deposit transfer")
//    public Mono<ResponseEntity<ApiResponse<Void>>> deleteDepositTransfer(
//            @RequestParam Long telegramId,
//            @RequestParam Long id,
//            ServerWebExchange exchange
//    ) {
//
//        return depositTransferService.deleteDepositTransfer(id, telegramId)
//                .then(Mono.fromSupplier(() -> ApiResponse.<Void>builder()
//                        .statusCode(HttpStatus.OK.value())
//                        .success(true)
//                        .message("Transfer deleted successfully")
//                        .path(exchange.getRequest().getPath().value())
//                        .timestamp(Instant.now())
//                        .build()
//                ))
//                .map(ResponseEntity::ok);
//    }
//}


package com.ebingo.backend.payment.controller.secured;

import com.ebingo.backend.common.TelegramAuthVerifier;
import com.ebingo.backend.common.dto.ApiResponse;
import com.ebingo.backend.payment.dto.DepositTransferDto;
import com.ebingo.backend.payment.dto.DepositTransferRequestDto;
import com.ebingo.backend.payment.service.DepositTransferService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/secured/deposit/transfers")
@RequiredArgsConstructor
@Tag(name = "Deposit Transfers Endpoint", description = "Deposit Transfers Endpoint")
public class DepositTransferController {

    private final DepositTransferService depositTransferService;
    private final TelegramAuthVerifier telegramAuthVerifier;
    private final ObjectMapper objectMapper;

    // ✅ Utility to extract Telegram user ID
    private Mono<Long> extractTelegramUserId(String telegramInitData, ServerWebExchange exchange) {
        Optional<Map<String, String>> initData = telegramAuthVerifier.verifyInitData(telegramInitData);
        if (initData.isEmpty()) {
            return Mono.error(new SecurityException("Invalid Telegram init data"));
        }

        try {
            String userJson = initData.get().get("user");
            if (userJson == null) return Mono.error(new IllegalArgumentException("Missing 'user' field in initData"));

            Map<String, Object> user = objectMapper.readValue(userJson, Map.class);
            Object idObj = user.get("id");
            if (idObj == null) return Mono.error(new IllegalArgumentException("Missing user ID in initData"));

            if (idObj instanceof Number n) return Mono.just(n.longValue());
            return Mono.just(Long.parseLong(idObj.toString()));
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalArgumentException("Invalid user JSON: " + e.getOriginalMessage()));
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Invalid user ID format: " + e.getMessage()));
        }
    }

    // ✅ Get paginated deposit transfers
    @GetMapping
    @Operation(summary = "Get paginated deposit transfers", description = "Get paginated deposit transfers")
    public Mono<ResponseEntity<ApiResponse<List<DepositTransferDto>>>> getPaginatedDepositTransfers(
            @RequestParam Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestHeader("x-init-data") String telegramInitData,
            ServerWebExchange exchange
    ) {
        return extractTelegramUserId(telegramInitData, exchange)
                .flatMap(userId ->
                        depositTransferService.getPaginatedDepositTransfer(userId, page, size, sortBy)
                                .collectList()
                                .map(transfers -> ApiResponse.<List<DepositTransferDto>>builder()
                                        .statusCode(HttpStatus.OK.value())
                                        .success(true)
                                        .message("Transfers retrieved successfully")
                                        .path(exchange.getRequest().getPath().value())
                                        .timestamp(Instant.now())
                                        .data(transfers)
                                        .build())
                                .map(ResponseEntity::ok)
                )
                .onErrorResume(ex -> {
                    log.error("Failed to retrieve deposit transfers: {}", ex.getMessage(), ex);
                    return Mono.just(ResponseEntity.status(
                                    ex instanceof SecurityException ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.<List<DepositTransferDto>>builder()
                                    .statusCode(ex instanceof SecurityException ? HttpStatus.UNAUTHORIZED.value() : HttpStatus.BAD_REQUEST.value())
                                    .success(false)
                                    .message(ex.getMessage())
                                    .path(exchange.getRequest().getPath().value())
                                    .timestamp(Instant.now())
                                    .build()));
                });
    }

    // ✅ Get a single deposit transfer
    @GetMapping("/{id}")
    @Operation(summary = "Get a single deposit transfer", description = "Get a single deposit transfer")
    public Mono<ResponseEntity<ApiResponse<DepositTransferDto>>> getASingleDepositTransfer(
            @PathVariable Long id,
            @RequestHeader("x-init-data") String telegramInitData,
            ServerWebExchange exchange
    ) {
        return extractTelegramUserId(telegramInitData, exchange)
                .flatMap(userId ->
                        depositTransferService.getASingleDepositTransfer(id, userId)
                                .map(transfer -> ApiResponse.<DepositTransferDto>builder()
                                        .statusCode(HttpStatus.OK.value())
                                        .success(true)
                                        .message("Transfer retrieved successfully")
                                        .path(exchange.getRequest().getPath().value())
                                        .timestamp(Instant.now())
                                        .data(transfer)
                                        .build())
                                .map(ResponseEntity::ok)
                )
                .onErrorResume(ex -> {
                    log.error("Failed to get deposit transfer: {}", ex.getMessage(), ex);
                    return Mono.just(ResponseEntity.status(
                                    ex instanceof SecurityException ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.<DepositTransferDto>builder()
                                    .statusCode(ex instanceof SecurityException ? HttpStatus.UNAUTHORIZED.value() : HttpStatus.BAD_REQUEST.value())
                                    .success(false)
                                    .message(ex.getMessage())
                                    .path(exchange.getRequest().getPath().value())
                                    .timestamp(Instant.now())
                                    .build()));
                });
    }

    // ✅ Create a deposit transfer
    @PostMapping
    @Operation(summary = "Create a deposit transfer", description = "Create a deposit transfer")
    public Mono<ResponseEntity<ApiResponse<DepositTransferDto>>> createDepositTransfer(
            @RequestHeader("x-init-data") String telegramInitData,
            @Valid @RequestBody DepositTransferRequestDto depositTransferDto,
            ServerWebExchange exchange
    ) {
        return extractTelegramUserId(telegramInitData, exchange)
                .flatMap(userId ->
                        depositTransferService.createDepositTransfer(depositTransferDto, userId)
                                .map(transfer -> ApiResponse.<DepositTransferDto>builder()
                                        .statusCode(HttpStatus.CREATED.value())
                                        .success(true)
                                        .message("Transfer created successfully")
                                        .path(exchange.getRequest().getPath().value())
                                        .timestamp(Instant.now())
                                        .data(transfer)
                                        .build())
                                .map(ResponseEntity::ok)
                )
                .onErrorResume(ex -> {
                    log.error("Failed to create deposit transfer: {}", ex.getMessage(), ex);
                    return Mono.just(ResponseEntity.status(
                                    ex instanceof SecurityException ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.<DepositTransferDto>builder()
                                    .statusCode(ex instanceof SecurityException ? HttpStatus.UNAUTHORIZED.value() : HttpStatus.BAD_REQUEST.value())
                                    .success(false)
                                    .message(ex.getMessage())
                                    .path(exchange.getRequest().getPath().value())
                                    .timestamp(Instant.now())
                                    .build()));
                });
    }

    // ✅ Delete a deposit transfer
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a single deposit transfer", description = "Delete a single deposit transfer")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteDepositTransfer(
            @PathVariable Long id,
            @RequestHeader("x-init-data") String telegramInitData,
            ServerWebExchange exchange
    ) {
        return extractTelegramUserId(telegramInitData, exchange)
                .flatMap(userId ->
                        depositTransferService.deleteDepositTransfer(id, userId)
                                .then(Mono.fromSupplier(() -> ApiResponse.<Void>builder()
                                        .statusCode(HttpStatus.OK.value())
                                        .success(true)
                                        .message("Transfer deleted successfully")
                                        .path(exchange.getRequest().getPath().value())
                                        .timestamp(Instant.now())
                                        .build()))
                                .map(ResponseEntity::ok)
                )
                .onErrorResume(ex -> {
                    log.error("Failed to delete deposit transfer: {}", ex.getMessage(), ex);
                    return Mono.just(ResponseEntity.status(
                                    ex instanceof SecurityException ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.<Void>builder()
                                    .statusCode(ex instanceof SecurityException ? HttpStatus.UNAUTHORIZED.value() : HttpStatus.BAD_REQUEST.value())
                                    .success(false)
                                    .message(ex.getMessage())
                                    .path(exchange.getRequest().getPath().value())
                                    .timestamp(Instant.now())
                                    .build()));
                });
    }
}
