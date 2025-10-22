package com.ebingo.backend.payment.controller.secured;

import com.ebingo.backend.common.TelegramAuthVerifier;
import com.ebingo.backend.common.dto.ApiResponse;
import com.ebingo.backend.payment.dto.InitiateDepositRequest;
import com.ebingo.backend.payment.dto.TransactionDto;
import com.ebingo.backend.payment.dto.WithdrawRequestDto;
import com.ebingo.backend.payment.enums.TransactionStatus;
import com.ebingo.backend.payment.enums.TransactionType;
import com.ebingo.backend.payment.service.TransactionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@Tag(name = "Transaction Secured Controller", description = "Transaction Secured Controller")
@RequestMapping("/api/v1/secured/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;
    private final TelegramAuthVerifier telegramAuthVerifier;

    @GetMapping
    @Operation(summary = "Get all transactions with pagination", description = "Get all transactions with pagination")
    public Mono<ResponseEntity<ApiResponse<List<TransactionDto>>>> getPaginatedTransactions(
            @Parameter(required = true, description = "Page number") @RequestParam Integer page,
            @Parameter(required = false, description = "Page size") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(required = false, description = "Sort by") @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestHeader(value = "x-init-data", required = true) String telegramInitData,
            ServerWebExchange exchange
    ) {
        // ✅ Verify Telegram init data
        Optional<Map<String, String>> initData = telegramAuthVerifier.verifyInitData(telegramInitData);
        if (initData.isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<List<TransactionDto>>builder()
                            .statusCode(HttpStatus.UNAUTHORIZED.value())
                            .success(false)
                            .message("Invalid telegram init data")
                            .path(exchange.getRequest().getPath().value())
                            .timestamp(Instant.now())
                            .build()
            ));
        }

        // ✅ Parse user JSON safely
        Map<String, Object> user;
        try {
            String userJson = initData.get().get("user");
            if (userJson == null) {
                return Mono.just(ResponseEntity.badRequest().body(
                        ApiResponse.<List<TransactionDto>>builder()
                                .statusCode(HttpStatus.BAD_REQUEST.value())
                                .success(false)
                                .message("Missing 'user' field in initData")
                                .path(exchange.getRequest().getPath().value())
                                .timestamp(Instant.now())
                                .build()));
            }
            user = objectMapper.readValue(userJson, Map.class);
        } catch (JsonProcessingException e) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ApiResponse.<List<TransactionDto>>builder()
                            .statusCode(HttpStatus.BAD_REQUEST.value())
                            .success(false)
                            .message("Invalid user data: " + e.getOriginalMessage())
                            .path(exchange.getRequest().getPath().value())
                            .timestamp(Instant.now())
                            .build()));
        }

        // ✅ Extract Long user ID safely
        Object idObj = user.get("id");
        long userId;
        try {
            if (idObj instanceof Number n) {
                userId = n.longValue();
            } else if (idObj instanceof String s) {
                userId = Long.parseLong(s);
            } else {
                return Mono.just(ResponseEntity.badRequest().body(
                        ApiResponse.<List<TransactionDto>>builder()
                                .statusCode(HttpStatus.BAD_REQUEST.value())
                                .success(false)
                                .message("Invalid user ID format")
                                .path(exchange.getRequest().getPath().value())
                                .timestamp(Instant.now())
                                .build()));
            }
        } catch (Exception e) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ApiResponse.<List<TransactionDto>>builder()
                            .statusCode(HttpStatus.BAD_REQUEST.value())
                            .success(false)
                            .message("Error parsing user ID: " + e.getMessage())
                            .path(exchange.getRequest().getPath().value())
                            .timestamp(Instant.now())
                            .build()));
        }

        // ✅ Fetch transactions
        return transactionService.getPaginatedTransaction(userId, page, size, sortBy)
                .collectList()
                .map(txns -> ApiResponse.<List<TransactionDto>>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Transactions retrieved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txns)
                        .build())
                .map(ResponseEntity::ok)
                .onErrorResume(ex -> Mono.just(ResponseEntity.internalServerError().body(
                        ApiResponse.<List<TransactionDto>>builder()
                                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .success(false)
                                .message("Internal server error: " + ex.getMessage())
                                .path(exchange.getRequest().getPath().value())
                                .timestamp(Instant.now())
                                .build()
                )));
    }


    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID", description = "Get transaction by ID")
    public Mono<ResponseEntity<ApiResponse<TransactionDto>>> getTransactionById(
            @RequestParam Long telegramId,
            @Parameter(required = true, description = "Transaction ID") @RequestParam Long id,
            ServerWebExchange exchange) {
        return transactionService.getTransactionById(id, telegramId)
                .map(txn -> ApiResponse.<TransactionDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Transaction is retrieved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txn)
                        .build()
                )
                .map(ResponseEntity::ok);
    }


    @PostMapping("/deposit/initiate-offline")
    @Operation(summary = "Initiate deposit", description = "Initiate deposit")
    public Mono<ResponseEntity<ApiResponse<TransactionDto>>> initiateDeposit(
            @Parameter(required = true, description = "Amount") @RequestBody InitiateDepositRequest depositRequest,
            @RequestParam Long telegramId,
            ServerWebExchange exchange
    ) {

        return transactionService.initiateOfflineDeposit(depositRequest, telegramId)
                .map(txn -> ApiResponse.<TransactionDto>builder()
                        .statusCode(HttpStatus.CREATED.value())
                        .success(true)
                        .message("Deposit initiated successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txn)
                        .build()
                )
                .map(ResponseEntity::ok);
    }


    @PutMapping("/deposit/confirm-offline")
    @Operation(summary = "Confirm deposit offline", description = "Confirm deposit offline")
    public Mono<ResponseEntity<ApiResponse<TransactionDto>>> confirmDepositOfflineByAdmin(
            @Parameter(required = true, description = "txnRef") @RequestParam String txnRef,
            @Parameter(required = false, description = "metaData") @RequestBody String metaData,
            @RequestParam Long approverTelegramId,
            ServerWebExchange exchange
    ) {

        return transactionService.confirmDepositOfflineByAdmin(txnRef, metaData, approverTelegramId)
                .map(txn -> ApiResponse.<TransactionDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Deposit confirmed successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txn)
                        .build()
                )
                .map(ResponseEntity::ok);
    }


    @GetMapping("/by-status")
    @Operation(summary = "Get transactions by status", description = "Get transactions by status")
    public Mono<ResponseEntity<ApiResponse<List<TransactionDto>>>> getPaginatedTransactionsByStatusForAdmin(
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam String sortBy,
            ServerWebExchange exchange
    ) {
        return transactionService.getPaginatedDepositsByStatusForAdmin(status, type, page, size, sortBy)
                .collectList()
                .map(txns -> ApiResponse.<List<TransactionDto>>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Transactions retrieved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txns)
                        .build())
                .map(ResponseEntity::ok);
    }


    @PutMapping("/deposit/{id}/cancel-offline")
    @Operation(summary = "Cancel deposit offline", description = "Cancel deposit offline")
    public Mono<ResponseEntity<ApiResponse<TransactionDto>>> cancelDepositOffline(
            @Parameter(required = true, description = "id") @PathVariable Long id,
            ServerWebExchange exchange
    ) {

        return transactionService.cancelDepositOffline(id)
                .map(txn -> ApiResponse.<TransactionDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Deposit cancelled successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txn)
                        .build()
                )
                .map(ResponseEntity::ok);
    }


    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw", description = "Withdraw")
    public Mono<ResponseEntity<ApiResponse<TransactionDto>>> withdraw(
            @RequestParam Long telegramId,
            @Valid @RequestBody WithdrawRequestDto withdrawRequestDto,
            ServerWebExchange exchange) {

        return transactionService.withdraw(withdrawRequestDto, telegramId)
                .map(txn -> ApiResponse.<TransactionDto>builder()
                        .statusCode(HttpStatus.CREATED.value())
                        .success(true)
                        .message("Withdraw created successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txn)
                        .build())
                .map(ResponseEntity::ok);
    }


    @PostMapping("/withdraw/approve")
    @Operation(summary = "Approve Withdrawal", description = "Admin approves a pending withdrawal")
    public Mono<ResponseEntity<ApiResponse<TransactionDto>>> approveWithdrawal(
            @RequestParam String txnRef,
            @RequestParam Long approverTelegramId,
            ServerWebExchange exchange) {

        return transactionService.confirmWithdrawalByAdmin(txnRef, approverTelegramId)
                .map(txn -> ApiResponse.<TransactionDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Withdrawal approved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txn)
                        .build())
                .map(ResponseEntity::ok);
    }


    @PostMapping("/withdraw/reject")
    @Operation(summary = "Reject Withdrawal", description = "Admin rejects a pending withdrawal")
    public Mono<ResponseEntity<ApiResponse<TransactionDto>>> rejectWithdrawal(
            @RequestParam String txnRef,
            @RequestParam(required = false) String reason,
            @RequestParam Long approverTelegramId,
            ServerWebExchange exchange) {

        return transactionService.rejectWithdrawalByAdmin(txnRef, reason, approverTelegramId)
                .map(txn -> ApiResponse.<TransactionDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Withdrawal rejected successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txn)
                        .build())
                .map(ResponseEntity::ok);
    }


    @PatchMapping("/{txnRef}/change-status")
    @Operation(summary = "Change Transaction status", description = "Change Transaction status")
    public Mono<ResponseEntity<ApiResponse<TransactionDto>>> changeStatus(
            @RequestParam Long approverTelegramId,
            @PathVariable String txnRef,
            @RequestParam TransactionStatus status,
            ServerWebExchange exchange) {

        return transactionService.changeTransactionStatus(txnRef, status, approverTelegramId)
                .map(txn -> ApiResponse.<TransactionDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Withdrawal approved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txn)
                        .build())
                .map(ResponseEntity::ok);
    }


}
