package com.ebingo.backend.payment.controller.secured;

import com.ebingo.backend.common.dto.ApiResponse;
import com.ebingo.backend.payment.dto.DepositTransferDto;
import com.ebingo.backend.payment.dto.DepositTransferRequestDto;
import com.ebingo.backend.payment.service.DepositTransferService;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/api/v1/secured/deposit/transfers")
@RequiredArgsConstructor
@Tag(name = "Deposit Transfers Endpoint", description = "Deposit Transfers Endpoint")
public class DepositTransferController {

    private final DepositTransferService depositTransferService;

    @GetMapping
    @Operation(summary = "Get paginated deposit transfers", description = "Get paginated deposit transfers")
    public Mono<ResponseEntity<ApiResponse<List<DepositTransferDto>>>> getPaginatedDepositTransfers(
            @RequestParam Long telegramId,
            @RequestParam Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam String sortBy,
            ServerWebExchange exchange
    ) {

        return depositTransferService.getPaginatedDepositTransfer(telegramId, page, size, sortBy)
                .collectList()
                .map(transfers -> ApiResponse.<List<DepositTransferDto>>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Transfers retrieved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(transfers)
                        .build())
                .map(ResponseEntity::ok);
    }


    @GetMapping("/{id}")
    @Operation(summary = "Get a single deposit transfer", description = "Get a single deposit transfer")
    public Mono<ResponseEntity<ApiResponse<DepositTransferDto>>> getASingleDepositTransfer(
            @RequestParam Long telegramId,
            @RequestParam Long id,
            ServerWebExchange exchange
    ) {

        return depositTransferService.getASingleDepositTransfer(id, telegramId)
                .map(transfer -> ApiResponse.<DepositTransferDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Transfer retrieved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(transfer)
                        .build())
                .map(ResponseEntity::ok);
    }


    @PostMapping
    @Operation(summary = "Create a deposit transfer", description = "Create a deposit transfer")
    public Mono<ResponseEntity<ApiResponse<DepositTransferDto>>> createDepositTransfer(
            @RequestParam Long telegramId,
            @Valid @RequestBody DepositTransferRequestDto depositTransferDto,
            ServerWebExchange exchange
    ) {

        return depositTransferService.createDepositTransfer(depositTransferDto, telegramId)
                .map(transfer -> ApiResponse.<DepositTransferDto>builder()
                        .statusCode(HttpStatus.CREATED.value())
                        .success(true)
                        .message("Transfer created successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(transfer)
                        .build())
                .map(ResponseEntity::ok);
    }


    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a single deposit transfer", description = "Delete a single deposit transfer")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteDepositTransfer(
            @RequestParam Long telegramId,
            @RequestParam Long id,
            ServerWebExchange exchange
    ) {

        return depositTransferService.deleteDepositTransfer(id, telegramId)
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Transfer deleted successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .build()
                ))
                .map(ResponseEntity::ok);
    }
}
