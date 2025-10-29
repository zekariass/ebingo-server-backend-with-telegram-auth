package com.ebingo.backend.payment.controller.secured;

import com.ebingo.backend.common.dto.ApiResponse;
import com.ebingo.backend.common.telegram.AuthenticatedTelegramUser;
import com.ebingo.backend.common.telegram.TelegramUser;
import com.ebingo.backend.payment.dto.DepositTransferDto;
import com.ebingo.backend.payment.dto.DepositTransferRequestDto;
import com.ebingo.backend.payment.service.DepositTransferService;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/secured/deposit/transfers")
@RequiredArgsConstructor
@Tag(name = "Deposit Transfers Endpoint", description = "Deposit Transfers Endpoint")
public class DepositTransferController {

    private final DepositTransferService depositTransferService;

    // Get paginated deposit transfers
//    @GetMapping
//    @Operation(summary = "Get paginated deposit transfers", description = "Retrieve paginated deposit transfers for the authenticated Telegram user")
//    public Mono<ResponseEntity<ApiResponse<PageResponse<DepositTransferDto>>>> getPaginatedDepositTransfers(
//            @AuthenticatedTelegramUser TelegramUser user,
//            @RequestParam(defaultValue = "1") Integer page,
//            @RequestParam(defaultValue = "10") Integer size,
//            @RequestParam(defaultValue = "createdAt") String sortBy,
//            ServerWebExchange exchange
//    ) {
//        return depositTransferService.getPaginatedDepositTransfer(user.id(), page, size, sortBy)
//                .collectList()
//                .zipWith(depositTransferService.countAllDepositTransfers(user.getId()))
//                .map(tuple -> {
//                    List<DepositTransferDto> transfers = tuple.getT1();
//                    long totalElements = tuple.getT2();
//
//                    PageResponse<DepositTransferDto> pageResponse = new PageResponse<>(
//                            transfers, page, size, totalElements
//                    );
//
//                    ApiResponse<PageResponse<DepositTransferDto>> apiResponse = ApiResponse.<PageResponse<DepositTransferDto>>builder()
//                            .statusCode(HttpStatus.OK.value())
//                            .success(true)
//                            .message("Transfers retrieved successfully")
//                            .path(exchange.getRequest().getPath().value())
//                            .data(pageResponse)
//                            .build();
//
//                    return ResponseEntity.ok(apiResponse);
//                })
//                .onErrorResume(ex -> handleError(ex, exchange, "Failed to retrieve deposit transfers"));
//    }


    // Get a single deposit transfer
    @GetMapping("/{id}")
    @Operation(summary = "Get a single deposit transfer", description = "Retrieve a single deposit transfer for the authenticated Telegram user")
    public Mono<ResponseEntity<ApiResponse<DepositTransferDto>>> getASingleDepositTransfer(
            @AuthenticatedTelegramUser TelegramUser user,
            @PathVariable Long id,
            ServerWebExchange exchange
    ) {
        return depositTransferService.getASingleDepositTransfer(id, user.id())
                .map(transfer -> ApiResponse.<DepositTransferDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Transfer retrieved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(transfer)
                        .build())
                .map(ResponseEntity::ok)
                .onErrorResume(ex -> handleError(ex, exchange, "Failed to retrieve deposit transfer"));
    }

    // Create a deposit transfer
    @PostMapping
    @Operation(summary = "Create a deposit transfer", description = "Create a deposit transfer for the authenticated Telegram user")
    public Mono<ResponseEntity<ApiResponse<DepositTransferDto>>> createDepositTransfer(
            @AuthenticatedTelegramUser TelegramUser user,
            @Valid @RequestBody DepositTransferRequestDto depositTransferDto,
            ServerWebExchange exchange
    ) {
        return depositTransferService.createDepositTransfer(depositTransferDto, user.id())
                .map(transfer -> ApiResponse.<DepositTransferDto>builder()
                        .statusCode(HttpStatus.CREATED.value())
                        .success(true)
                        .message("Transfer created successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(transfer)
                        .build())
                .map(ResponseEntity::ok)
                .onErrorResume(ex -> handleError(ex, exchange, "Failed to create deposit transfer"));
    }

    // Delete a deposit transfer
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a deposit transfer", description = "Delete a deposit transfer for the authenticated Telegram user")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteDepositTransfer(
            @AuthenticatedTelegramUser TelegramUser user,
            @PathVariable Long id,
            ServerWebExchange exchange
    ) {
        return depositTransferService.deleteDepositTransfer(id, user.id())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Transfer deleted successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .build()))
                .map(ResponseEntity::ok)
                .onErrorResume(ex -> handleError(ex, exchange, "Failed to delete deposit transfer"));
    }

    // Centralized error handler for all endpoints
    private <T> Mono<ResponseEntity<ApiResponse<T>>> handleError(Throwable ex, ServerWebExchange exchange, String defaultMessage) {
        log.error("{}: {}", defaultMessage, ex.getMessage(), ex);

        HttpStatus status = (ex instanceof SecurityException)
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.BAD_REQUEST;

        return Mono.just(ResponseEntity.status(status)
                .body(ApiResponse.<T>builder()
                        .statusCode(status.value())
                        .success(false)
                        .message(ex.getMessage() != null ? ex.getMessage() : defaultMessage)
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .build()));
    }
}
