package com.ebingo.backend.payment.controller.secured;

import com.ebingo.backend.common.dto.ApiResponse;
import com.ebingo.backend.common.dto.PageResponse;
import com.ebingo.backend.common.telegram.AuthenticatedTelegramUser;
import com.ebingo.backend.common.telegram.TelegramUser;
import com.ebingo.backend.payment.dto.*;
import com.ebingo.backend.payment.service.PaymentMethodService;
import com.ebingo.backend.payment.service.PaymentOrderService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/secured/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    //    private final paymentOrderService paymentOrderService;
    private final PaymentOrderService paymentOrderService;
    private final PaymentMethodService paymentMethodService;

    @PostMapping("/order")
    public Mono<ResponseEntity<ApiResponse<PaymentOrderResponseDto>>> createOrder(
            @RequestBody PaymentOrderRequestDto dto,
            @AuthenticatedTelegramUser TelegramUser user,
            ServerWebExchange exchange
    ) {

        log.info("================>>> Creating payment order for user {}", user.id());
        dto.setUserId(user.id());
        return paymentOrderService.createOrder(dto, user)
                .map(order -> ResponseEntity.ok(
                        ApiResponse.<PaymentOrderResponseDto>builder()
                                .statusCode(200)
                                .success(true)
                                .message("Payment order created successfully")
                                .data(order)
                                .timestamp(Instant.now())
                                .path(exchange.getRequest().getPath().value())
                                .build()
                ));
    }


    @PostMapping("/order/initiate")
    public Mono<ResponseEntity<ApiResponse<PaymentInitiateResponseDto>>> initiatePayment(
            @RequestBody PaymentInitiateRequestDto dto,
            @AuthenticatedTelegramUser TelegramUser user,
            ServerWebExchange exchange
    ) {

        log.info("================>>> Initiating payment for user {}", user.id());
//        dto.setUserId(user.id());
        return paymentOrderService.initiatePaymentOnline(dto)
                .map(payment -> ResponseEntity.ok(
                        ApiResponse.<PaymentInitiateResponseDto>builder()
                                .statusCode(200)
                                .success(true)
                                .message("Payment successful")
                                .data(payment)
                                .timestamp(Instant.now())
                                .path(exchange.getRequest().getPath().value())
                                .build()
                ));
    }


//    @GetMapping("/orders/pending")
//    public Mono<ResponseEntity<ApiResponse<List<PaymentOrderDto>>>> getUserPendingOrders(
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "10") int size,
//            @RequestParam(defaultValue = "createdAt") String sortBy,
//            @AuthenticatedTelegramUser TelegramUser user,
//            ServerWebExchange exchange
//    ) {
//        return paymentOrderService.getUserPendingOrders(user.id(), page, size, sortBy)
//                .collectList()
//                .map(orders -> ResponseEntity.ok(
//                        ApiResponse.<List<PaymentOrderDto>>builder()
//                                .statusCode(HttpStatus.OK.value())
//                                .success(true)
//                                .message("Pending orders retrieved successfully")
//                                .data(orders)
//                                .timestamp(Instant.now())
//                                .path(exchange.getRequest().getPath().value())
//                                .build()
//                ))
//                .onErrorResume(ex -> {
//                    log.error("Failed to fetch pending orders for user {}: {}", user.id(), ex.getMessage(), ex);
//                    return Mono.just(ResponseEntity.internalServerError().body(
//                            ApiResponse.<List<PaymentOrderDto>>builder()
//                                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
//                                    .success(false)
//                                    .message("Failed to retrieve pending orders: " + ex.getMessage())
//                                    .timestamp(Instant.now())
//                                    .path(exchange.getRequest().getPath().value())
//                                    .build()
//                    ));
//                });
//    }


    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw", description = "Withdraw")
    public Mono<ResponseEntity<ApiResponse<WithdrawalResponseDto>>> withdraw(
            @Valid @RequestBody WithdrawRequestDto withdrawRequestDto,
            @AuthenticatedTelegramUser TelegramUser user,
            ServerWebExchange exchange) {

        return paymentOrderService.withdraw(withdrawRequestDto, user.id())
                .map(txn -> ApiResponse.<WithdrawalResponseDto>builder()
                        .statusCode(HttpStatus.CREATED.value())
                        .success(true)
                        .message("Withdraw created successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txn)
                        .build())
                .map(ResponseEntity::ok);
    }


    @PostMapping("/withdraw/offline/approve")
    @Operation(summary = "Approve Withdrawal", description = "Admin approves a pending withdrawal")
    public Mono<ResponseEntity<ApiResponse<PaymentOrderDto>>> approveWithdrawal(
            @RequestBody WithdrawalApprovalRequestDto dto,
            @AuthenticatedTelegramUser TelegramUser admin,
            ServerWebExchange exchange) {

        return paymentOrderService.confirmWithdrawalByAdmin(dto, admin.id())
                .map(txn -> ApiResponse.<PaymentOrderDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Withdrawal approved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(txn)
                        .build())
                .map(ResponseEntity::ok);
    }


    // Admin endpoint
    @GetMapping("/admin/withdrawals")
    public Mono<ResponseEntity<ApiResponse<PageResponse<PaymentOrderDto>>>> getAdminWithdrawals(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @AuthenticatedTelegramUser TelegramUser admin,
            ServerWebExchange exchange
    ) {
        return paymentOrderService.getAdminWithdrawals(status, page, size)
                .map(pageResponse -> ApiResponse.<PageResponse<PaymentOrderDto>>builder()
                        .success(true)
                        .statusCode(200)
                        .message("Withdrawals fetched successfully")
                        .path(exchange.getRequest().getPath().value())
                        .data(pageResponse)
                        .build()
                )
                .map(ResponseEntity::ok);
    }

    // User endpoint
    @GetMapping("/user/withdrawals")
    public Mono<ResponseEntity<ApiResponse<PageResponse<PaymentOrderDto>>>> getUserWithdrawals(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @AuthenticatedTelegramUser TelegramUser user,
            ServerWebExchange exchange
    ) {
        return paymentOrderService.getUserWithdrawals(user.id(), status, page, size)
                .map(pageResponse -> ApiResponse.<PageResponse<PaymentOrderDto>>builder()
                        .success(true)
                        .statusCode(200)
                        .message("User withdrawals fetched successfully")
                        .path(exchange.getRequest().getPath().value())
                        .data(pageResponse)
                        .build()
                )
                .map(ResponseEntity::ok);
    }

    @GetMapping("/admin/deposits")
    public Mono<ResponseEntity<ApiResponse<PageResponse<PaymentOrderDto>>>> getAdminDeposits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @AuthenticatedTelegramUser TelegramUser admin,
            ServerWebExchange exchange
    ) {
        return paymentOrderService.getAdminDeposits(status, page, size)
                .map(pageResponse -> ApiResponse.<PageResponse<PaymentOrderDto>>builder()
                        .success(true)
                        .statusCode(200)
                        .message("Deposits fetched successfully")
                        .path(exchange.getRequest().getPath().value())
                        .data(pageResponse)
                        .build()
                )
                .map(ResponseEntity::ok);
    }

    // User endpoint
    @GetMapping("/user/deposits")
    public Mono<ResponseEntity<ApiResponse<PageResponse<PaymentOrderDto>>>> getUserDeposits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @AuthenticatedTelegramUser TelegramUser user,
            ServerWebExchange exchange
    ) {
        return paymentOrderService.getUserDeposits(user.id(), status, page, size)
                .map(pageResponse -> ApiResponse.<PageResponse<PaymentOrderDto>>builder()
                        .success(true)
                        .statusCode(200)
                        .message("User deposits fetched successfully")
                        .path(exchange.getRequest().getPath().value())
                        .data(pageResponse)
                        .build()
                )
                .map(ResponseEntity::ok);
    }

}
