package com.ebingo.backend.payment.controller.secured;

import com.ebingo.backend.common.dto.ApiResponse;
import com.ebingo.backend.payment.dto.WalletDto;
import com.ebingo.backend.payment.service.WalletService;
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

@RestController
@Tag(name = "Wallet Secured Controller", description = "Wallet Secured Controller")
@RequestMapping("/api/v1/secured/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;

    @GetMapping
    @Operation(summary = "Get wallet", description = "Get wallet")
    public Mono<ResponseEntity<ApiResponse<WalletDto>>> getWallet(
            @Parameter(required = true, description = "User Supabase ID") @RequestParam String userSupabaseId,
            ServerWebExchange exchange
    ) {
        return walletService.getWalletBySupabaseId(userSupabaseId)
                .map(txn -> ApiResponse.<WalletDto>builder()
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

}
