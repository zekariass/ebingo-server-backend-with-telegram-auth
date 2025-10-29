package com.ebingo.backend.user.controller._public;

import com.ebingo.backend.common.dto.ApiResponse;
import com.ebingo.backend.payment.service.WalletService;
import com.ebingo.backend.user.dto.UserProfileCreateDto;
import com.ebingo.backend.user.dto.UserProfileDto;
import com.ebingo.backend.user.mappers.UserProfileMapper;
import com.ebingo.backend.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@RestController
@Tag(name = "User Profile Public Controller", description = "User Profile Public Controller")
@RequestMapping("/api/v1/public/user-profile")
@Slf4j
public class UserProfilePublicController {

    private final UserProfileService userProfileService;
    private final WalletService walletService;
    private final ReactiveTransactionManager transactionManager;


    public UserProfilePublicController(UserProfileService userProfileService, WalletService walletService, ReactiveTransactionManager transactionManager) {
        this.userProfileService = userProfileService;
        this.walletService = walletService;
        this.transactionManager = transactionManager;
    }


    @PostMapping("/register")
    @Operation(summary = "Create user profile", description = "Create user profile")
    public Mono<ResponseEntity<ApiResponse<UserProfileDto>>> createUserProfile(
            @Parameter(required = true, description = "User profile")
            @Valid @RequestBody UserProfileCreateDto userProfileDto,
            ServerWebExchange exchange
    ) {

        log.info("=================================>>> Creating user profile: {}", userProfileDto);
        // Define transactional operator (you should already have it injected in the service/controller)
        TransactionalOperator operator = TransactionalOperator.create(transactionManager);

        BigDecimal bonusAmount = BigDecimal.valueOf(10); // Example bonus amount for new wallets
        Mono<UserProfileDto> createUserAndWallet = userProfileService.createUserProfile(userProfileDto)
                .flatMap(userProfile ->
                        walletService.createWallet(UserProfileMapper.toEntity(userProfile), bonusAmount)
                                .thenReturn(userProfile)
                );

        // Wrap both in one transaction
        return operator.transactional(createUserAndWallet)
                .map(userProfile -> ApiResponse.<UserProfileDto>builder()
                        .statusCode(HttpStatus.CREATED.value())
                        .success(true)
                        .message("User profile created successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(userProfile)
                        .build()
                )
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .doOnSubscribe(s -> log.info("Creating user profile and wallet"))
                .doOnError(e -> log.error("Failed to create user + wallet: {}", e.getMessage(), e));
    }


    @PostMapping("/initData")
    public Mono<Map<String, Object>> me(@RequestBody String initData) {
        log.info("====================>>>>>>>> Init data: {}", initData);
        return Mono.just(Map.of("status", "ok"));
    }
}
