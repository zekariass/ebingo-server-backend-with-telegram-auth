package com.ebingo.backend.payment.controller.secured;

import com.ebingo.backend.common.dto.ApiResponse;
import com.ebingo.backend.payment.dto.GameTransactionDto;
import com.ebingo.backend.payment.service.GameTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/secured/game/transaction")
@RequiredArgsConstructor
@Tag(name = "Game Transaction Management", description = "Game Transaction Management")
public class GameTransactionController {

    private final GameTransactionService gameTransactionService;

    @GetMapping
    @Operation(summary = "Get paginated game transactions", description = "Get paginated game transactions")
    public Mono<ResponseEntity<ApiResponse<List<GameTransactionDto>>>> getPaginatedGameTransactions(
            @RequestParam String phoneNumber,
            @Parameter(required = true, description = "Page number") @RequestParam Integer page,
            @Parameter(required = false, description = "Page size") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(required = false, description = "Sort by") @RequestParam(defaultValue = "createdAt") String sortBy,
            ServerWebExchange exchange
    ) {
        return gameTransactionService.getPaginatedGameTransactions(phoneNumber, page, size, sortBy)
                .collectList()
                .map(txns -> ApiResponse.<List<GameTransactionDto>>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Transactions retrieved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txns)
                        .build())
                .map(ResponseEntity::ok);
    }


    @GetMapping("/{id}")
    @Operation(summary = "Get game transaction by ID", description = "Get game transaction by ID")
    public Mono<ResponseEntity<ApiResponse<GameTransactionDto>>> getGameTransactionById(
            @RequestParam String phoneNumber,
            @Parameter(required = true, description = "Game Transaction ID") @RequestParam Long id,
            ServerWebExchange exchange) {
        return gameTransactionService.getTransactionById(id, phoneNumber)
                .map(txn -> ApiResponse.<GameTransactionDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Game transaction is retrieved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txn)
                        .build()
                )
                .map(ResponseEntity::ok);
    }


}
