package com.ebingo.backend.payment.service;

import com.ebingo.backend.common.dto.PageResponse;
import com.ebingo.backend.common.telegram.TelegramUser;
import com.ebingo.backend.payment.dto.*;
import com.ebingo.backend.payment.entity.PaymentMethod;
import com.ebingo.backend.payment.entity.PaymentOrder;
import com.ebingo.backend.payment.entity.Wallet;
import com.ebingo.backend.payment.enums.PaymentOrderStatus;
import com.ebingo.backend.payment.enums.TransactionType;
import com.ebingo.backend.payment.enums.WithdrawalMode;
import com.ebingo.backend.payment.mappers.PaymentMethodMapper;
import com.ebingo.backend.payment.mappers.PaymentOrderMapper;
import com.ebingo.backend.payment.mappers.WalletMapper;
import com.ebingo.backend.payment.repository.PaymentOrderRepository;
import com.ebingo.backend.payment.service.payment_strategy.PaymentStrategy;
import com.ebingo.backend.payment.service.payment_strategy.PaymentStrategyFactory;
import com.ebingo.backend.payment.service.withdrawal_strategy.WithdrawalStrategy;
import com.ebingo.backend.payment.service.withdrawal_strategy.WithdrawalStrategyFactory;
import com.ebingo.backend.system.exceptions.ResourceNotFoundException;
import com.ebingo.backend.user.dto.UserProfileDto;
import com.ebingo.backend.user.mappers.UserProfileMapper;
import com.ebingo.backend.user.service.UserProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrderServiceImpl implements PaymentOrderService {

    private final PaymentOrderRepository orderRepo;
    private final PaymentStrategyFactory strategyFactory;
    private final WithdrawalStrategyFactory withdrawalStrategyFactory;
    private final ObjectMapper mapper;
    private final WalletService walletService; // existing service
    private final PaymentMethodService paymentMethodService;
    private final UserProfileService userProfileService;
    private final TransactionService transactionService;
    private final TransactionalOperator transactionalOperator;
    private final AddisPayClientService addisPayClientService;


    @Override
    public Mono<PaymentOrderResponseDto> createOrder(PaymentOrderRequestDto dto, TelegramUser telegramUser) {
        String uuidPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        int randomPart = (int) (Math.random() * 900) + 100; // ensures 3-digit number: 100–999
        String txnRef = "PO-" + uuidPart + randomPart;

        PaymentOrder order = PaymentOrderMapper.toEntity(dto, txnRef);
        order.setStatus(PaymentOrderStatus.PENDING);
        order.setNonce(UUID.randomUUID().toString());
        order.setAmount(dto.getAmount());
        order.setReason(dto.getReason() != null ? dto.getReason() : "Deposit");
        order.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : "ETB");

        return transactionalOperator.execute(status ->
                userProfileService.getUserProfileByTelegramId(Long.parseLong(telegramUser.rawData().get("id").toString()))
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found")))
                        .flatMap(user -> {
                            order.setUserId(user.getId());

                            return paymentMethodService.getPaymentMethodById(dto.getPaymentMethodId())
                                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment method not found")))
                                    .flatMap(paymentMethod -> {
                                        order.setPaymentMethodId(paymentMethod.getId());
                                        order.setPhoneNumber(user.getPhone());
                                        order.setInstructionsUrl(paymentMethod.getInstructionUrl());

                                        if (dto.getMetadata() != null) {
                                            try {
                                                order.setMetaData(mapper.writeValueAsString(dto.getMetadata()));
                                            } catch (Exception ex) {
                                                order.setMetaData(null);
                                                log.warn("Failed to serialize metadata: {}", ex.getMessage());
                                            }
                                        }

                                        return orderRepo.save(order)
                                                .flatMap(savedOrder -> {
                                                    PaymentMethod method = PaymentMethodMapper.toEntity(paymentMethod);
                                                    PaymentStrategy strategy = strategyFactory.getStrategy(method);

                                                    return strategy.initiateOrder(savedOrder, dto, method, user)
                                                            .flatMap(resp -> {
                                                                // update order state
                                                                if (resp.getProviderUuid() != null) {
                                                                    savedOrder.setProviderOrderRef(resp.getProviderUuid());
                                                                    savedOrder.setStatus(PaymentOrderStatus.INITIATED);
                                                                } else if (resp.getInstructionsUrl() != null) {
                                                                    savedOrder.setInstructionsUrl(resp.getInstructionsUrl());
                                                                    savedOrder.setStatus(PaymentOrderStatus.AWAITING_APPROVAL);
                                                                }

                                                                return orderRepo.save(savedOrder)
                                                                        .thenReturn(resp);
                                                            });
                                                });
                                    });
                        })
                        .onErrorResume(ex -> {
                            log.error("Error creating payment order: {}", ex.getMessage(), ex);
                            status.setRollbackOnly(); // ensure rollback
                            return Mono.error(ex);
                        })
        ).single();
    }


    /**
     * Admin confirms offline order -> mark completed and credit wallet
     */
//    public Mono<Void> confirmOfflineOrder(Long orderId, Long adminTelegramId) {
//        return transactionalOperator.execute(status ->
//                userProfileService.getUserProfileByTelegramId(adminTelegramId)
//                        .switchIfEmpty(Mono.error(new UnauthorizedException("Admin not found")))
//                        .flatMap(adminUser -> {
//                            if (adminUser.getRole() != UserRole.ADMIN) {
//                                return Mono.error(new UnauthorizedException("Unauthorized: user is not an admin"));
//                            }
//
//                            return orderRepo.findById(orderId)
//                                    .switchIfEmpty(Mono.error(new RuntimeException("Order not found")))
//                                    .flatMap(order -> {
//                                        order.setStatus(PaymentOrderStatus.COMPLETED);
//                                        order.setApprovedBy(adminUser.getId());
//
//                                        return orderRepo.save(order)
//                                                .then(transactionService.createTransaction(order))
//                                                .then(walletService.credit(
//                                                        order.getUserId(),
//                                                        order.getAmount(),
//                                                        "Offline deposit",
//                                                        order.getTxnType(),
//                                                        null
//                                                ));
//                                    });
//                        })
//        ).then(); // Transaction completes when the Mono chain completes
//    }


    /**
     * Process Addispay callback — find order by provider ref and credit wallet if success.
     */

    @Override
    public Mono<Void> processAddisPayCallbackForSuccess(
            String sessionUuid,
            String paymentStatus,
            String totalAmount,
            String orderId,
            String nonce,
            String addisPayTransactionId,
            String thirdPartyTransactionRef) {

        log.info("Processing AddisPay callback for sessionUuid: {}, orderId: {}, paymentStatus: {}, totalAmount: {}, nonce: {}, addisPayTransactionId: {}, thirdPartyTransactionRef: {}", sessionUuid, orderId, paymentStatus, totalAmount, nonce, addisPayTransactionId, thirdPartyTransactionRef);

        if (StringUtils.isEmpty(sessionUuid)) {
            return Mono.error(new RuntimeException("Missing sessionUuid"));
        }

        return orderRepo.findByProviderOrderRef(sessionUuid)
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found for session: " + sessionUuid)))
                .flatMap(order -> {
                    log.info("Order found: {}", order.getId());

                    // Normalize status
                    String normalizedStatus = paymentStatus == null ? "" : paymentStatus.trim().toLowerCase();

                    Map<String, Object> metadata = Map.of(
                            "sessionUuid", sessionUuid,
                            "status", normalizedStatus,
                            "totalAmount", totalAmount != null ? totalAmount : "null",
                            "nonce", nonce != null ? nonce : "null",
                            "addisPayTransactionId", addisPayTransactionId != null ? addisPayTransactionId : "null",
                            "thirdPartyTransactionRef", thirdPartyTransactionRef != null ? thirdPartyTransactionRef : "null"
                    );

                    log.info("================================Processing Callback Metadata: {}", metadata);

                    ObjectMapper objectMapper = new ObjectMapper();
                    String metadataJson;
                    try {
                        metadataJson = objectMapper.writeValueAsString(metadata);
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to serialize metadata", e));
                    }

                    order.setMetaData(metadataJson);

                    if (normalizedStatus.contains("success") || normalizedStatus.contains("completed")) {
                        order.setStatus(PaymentOrderStatus.COMPLETED);

                        return orderRepo.save(order)
                                .flatMap(savedOrder -> walletService.credit(
                                        savedOrder.getUserId(),
                                        savedOrder.getAmount(),
                                        "AddisPay deposit",
                                        savedOrder.getTxnType(),
                                        metadata
                                ));
                    } else {
                        order.setStatus(PaymentOrderStatus.FAILED);
                        return orderRepo.save(order).then();
                    }
                })
                .doOnSuccess(v -> log.info("Order processing completed for sessionUuid: {}", sessionUuid))
                .doOnError(e -> log.error("Error processing AddisPay success callback: {}", e.getMessage(), e))
                .then();
    }


    @Override
    public Mono<Void> processAddisPayCallbackForError(
            String sessionUuid,
            String paymentStatus,
            String totalAmount,
            String orderId,
            String nonce,
            String addisTransactionId,
            String thirdPartyTransactionRef) {

        log.info("Processing AddisPay ERROR callback for sessionUuid: {}, orderId: {}, paymentStatus: {}",
                sessionUuid, orderId, paymentStatus);

        if (StringUtils.isEmpty(sessionUuid)) {
            return Mono.error(new RuntimeException("Missing sessionUuid"));
        }

        return orderRepo.findByProviderOrderRef(sessionUuid)
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found for session: " + sessionUuid)))
                .flatMap(order -> {
                    log.info("Order found for ERROR callback: {}", order.getId());

                    String normalizedStatus = paymentStatus == null ? "" : paymentStatus.trim().toLowerCase();

                    Map<String, Object> metadata = Map.of(
                            "sessionUuid", sessionUuid,
                            "status", normalizedStatus,
                            "totalAmount", totalAmount != null ? totalAmount : "null",
                            "nonce", nonce != null ? nonce : "null",
                            "addisPayTransactionId", addisTransactionId != null ? addisTransactionId : "null",
                            "thirdPartyTransactionRef", thirdPartyTransactionRef != null ? thirdPartyTransactionRef : "null"
                    );

                    ObjectMapper objectMapper = new ObjectMapper();
                    String metadataJson;
                    try {
                        metadataJson = objectMapper.writeValueAsString(metadata);
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to serialize metadata", e));
                    }

                    order.setMetaData(metadataJson);
                    order.setStatus(PaymentOrderStatus.FAILED);

                    return orderRepo.save(order);
                })
                .doOnSuccess(v -> log.info(" AddisPay ERROR callback processed for sessionUuid: {}", sessionUuid))
                .doOnError(e -> log.error("Error processing AddisPay error callback: {}", e.getMessage(), e))
                .then();
    }


    @Override
    public Mono<Void> processAddisPayCallbackCancel(
            String sessionUuid,
            String paymentStatus,
            String totalAmount,
            String orderId,
            String nonce,
            String addisTransactionId,
            String thirdPartyTransactionRef) {

        log.info("Processing AddisPay CANCEL callback for sessionUuid: {}, orderId: {}, paymentStatus: {}",
                sessionUuid, orderId, paymentStatus);

        if (StringUtils.isEmpty(sessionUuid)) {
            return Mono.error(new RuntimeException("Missing sessionUuid"));
        }

        return orderRepo.findByProviderOrderRef(sessionUuid)
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found for session: " + sessionUuid)))
                .flatMap(order -> {
                    log.info("Order found for CANCEL callback: {}", order.getId());

                    String normalizedStatus = paymentStatus == null ? "" : paymentStatus.trim().toLowerCase();

                    Map<String, Object> metadata = Map.of(
                            "sessionUuid", sessionUuid,
                            "status", normalizedStatus,
                            "totalAmount", totalAmount != null ? totalAmount : "null",
                            "nonce", nonce != null ? nonce : "null",
                            "addisPayTransactionId", addisTransactionId != null ? addisTransactionId : "null",
                            "thirdPartyTransactionRef", thirdPartyTransactionRef != null ? thirdPartyTransactionRef : "null"
                    );

                    ObjectMapper objectMapper = new ObjectMapper();
                    String metadataJson;
                    try {
                        metadataJson = objectMapper.writeValueAsString(metadata);
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to serialize metadata", e));
                    }

                    order.setMetaData(metadataJson);
                    order.setStatus(PaymentOrderStatus.CANCELLED);

                    return orderRepo.save(order);
                })
                .doOnSuccess(v -> log.info(" AddisPay CANCEL callback processed for sessionUuid: {}", sessionUuid))
                .doOnError(e -> log.error(" Error processing AddisPay cancel callback: {}", e.getMessage(), e))
                .then();
    }


    /**
     * Fetch all offline (manual) orders with pagination + sorting
     */
//    public Flux<PaymentOrderDto> getOfflineOrders(int page, int size, String sortBy) {
//        int pageNumber = Math.max(page, 1);
//        int pageSize = (size > 0 && size <= 100) ? size : 20;
//        long offset = (long) (pageNumber - 1) * pageSize;
//        String sortKey = normalizeSortKey(sortBy);
//
//        Flux<PaymentOrder> ordersFlux = switch (sortKey) {
//            case "amount" -> orderRepo.findOfflineOrdersOrderByAmountDesc(pageSize, offset);
//            case "status" -> orderRepo.findOfflineOrdersOrderByStatusDesc(pageSize, offset);
//            case "updatedat" -> orderRepo.findOfflineOrdersOrderByUpdatedAtDesc(pageSize, offset);
//            case "createdat" -> orderRepo.findOfflineOrdersOrderByCreatedAtDesc(pageSize, offset);
//            default -> orderRepo.findOfflineOrdersOrderByIdDesc(pageSize, offset);
//        };
//
//        return ordersFlux
//                .map(PaymentOrderMapper::toDto)
//                .doOnSubscribe(s -> log.info("Fetching offline orders, page={}, size={}, sortBy={}", page, size, sortBy))
//                .doOnError(e -> log.error("Failed to fetch offline orders: {}", e.getMessage(), e));
//    }

    /**
     * Fetch offline orders by status with pagination + sorting
     */
//    public Flux<PaymentOrderDto> getOfflineOrdersByStatus(String status, int page, int size, String sortBy) {
//        int pageNumber = Math.max(page, 1);
//        int pageSize = (size > 0 && size <= 100) ? size : 20;
//        long offset = (long) (pageNumber - 1) * pageSize;
//        String sortKey = normalizeSortKey(sortBy);
//
//        PaymentOrderStatus orderStatus;
//        try {
//            orderStatus = PaymentOrderStatus.valueOf(status.toUpperCase());
//        } catch (IllegalArgumentException e) {
//            return Flux.error(new IllegalArgumentException("Invalid payment order status: " + status));
//        }
//
//        Flux<PaymentOrder> ordersFlux = switch (sortKey) {
//            case "amount" -> orderRepo.findOfflineOrdersByStatusOrderByAmountDesc(orderStatus.name(), pageSize, offset);
//            case "updatedat" ->
//                    orderRepo.findOfflineOrdersByStatusOrderByUpdatedAtDesc(orderStatus.name(), pageSize, offset);
//            case "createdat" ->
//                    orderRepo.findOfflineOrdersByStatusOrderByCreatedAtDesc(orderStatus.name(), pageSize, offset);
//            default -> orderRepo.findOfflineOrdersByStatusOrderByIdDesc(orderStatus.name(), pageSize, offset);
//        };
//
//        return ordersFlux
//                .map(PaymentOrderMapper::toDto)
//                .doOnSubscribe(s -> log.info("Fetching offline orders by status={}, page={}, size={}, sortBy={}", status, page, size, sortBy))
//                .doOnError(e -> log.error("Failed to fetch offline orders by status {}: {}", status, e.getMessage(), e));
//    }

    /**
     * Fetch a user's pending orders with pagination + sorting
     */
//    public Flux<PaymentOrderDto> getUserPendingOrders(long userId, int page, int size, String sortBy) {
//        int pageNumber = Math.max(page, 1);
//        int pageSize = (size > 0 && size <= 100) ? size : 20;
//        long offset = (long) (pageNumber - 1) * pageSize;
//        String sortKey = normalizeSortKey(sortBy);
//
//        Flux<PaymentOrder> ordersFlux = switch (sortKey) {
//            case "amount" -> orderRepo.findPendingOrdersByUserIdOrderByAmountDesc(userId, pageSize, offset);
//            case "createdat" -> orderRepo.findPendingOrdersByUserIdOrderByCreatedAtDesc(userId, pageSize, offset);
//            default -> orderRepo.findPendingOrdersByUserIdOrderByIdDesc(userId, pageSize, offset);
//        };
//
//        return ordersFlux
//                .map(PaymentOrderMapper::toDto)
//                .doOnSubscribe(s -> log.info("Fetching pending orders for user={}, page={}, size={}, sortBy={}", userId, page, size, sortBy))
//                .doOnError(e -> log.error("Failed to fetch pending orders for user {}: {}", userId, e.getMessage(), e));
//    }
//
//    // --- Helpers ---
//    private String normalizeSortKey(String sortBy) {
//        return (sortBy != null) ? sortBy.toLowerCase() : "createdat";
//    }
    @Override
    public Mono<PaymentInitiateResponseDto> initiatePaymentOnline(PaymentInitiateRequestDto dto) {
        String phoneNumber = dto.getPhoneNumber();
        if (phoneNumber.contains("+")) {
            phoneNumber = phoneNumber.replace("+", "");
        }

        JsonNode payload = mapper.valueToTree(Map.of(
                "uuid", dto.getUuid(),
                "phone_number", phoneNumber,
                "encrypted_total_amount", dto.getEncryptedTotalAmount(),
                "merchant_name", dto.getMerchantName(),
                "selected_service", dto.getSelectedService(),
                "selected_bank", dto.getSelectedBank()
        ));

        log.info("AddisPay Payload: {}", payload.toPrettyString());

        return addisPayClientService.initiatePayment(payload)
                .flatMap(json -> {
                    log.info("==========> AddisPay initiate-payment response: {}", json.toPrettyString());

                    PaymentInitiateResponseDto responseDto = PaymentInitiateResponseDto.builder()
                            .message(json.path("message").asText(null))
                            .data(json.path("data").isNull() ? "" : json.path("data").toString())
                            .details(json.path("details").asText(null))
                            .statusCode(json.path("status_code").asInt(0))
                            .build();

                    return Mono.just(responseDto);
                })
                .onErrorResume(e -> {
                    log.error("Error initiating AddisPay payment: {}", e.getMessage(), e);

                    // Try marking the order as failed before rethrowing
                    return orderRepo.findByProviderOrderRef(dto.getUuid())
                            .flatMap(order -> {
                                order.setStatus(PaymentOrderStatus.FAILED);
                                order.setMetaData("AddisPay error: " + e.getMessage());
                                return orderRepo.save(order)
                                        .doOnSuccess(o -> log.warn("Order {} marked FAILED due to AddisPay error", o.getId()));
                            })
                            .then(Mono.error(new RuntimeException("AddisPay initiate-payment failed: " + e.getMessage(), e)));
                });
    }


//    =========================================================================

    @Override
    public Mono<PaymentOrderDto> confirmWithdrawalByAdmin(WithdrawalApprovalRequestDto dto, Long adminUserId) {
        log.info("Admin {} processing withdrawal order: {}", adminUserId, dto.getOrderId());

        Mono<PaymentOrderDto> approvalFlow = orderRepo.findById(dto.getOrderId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Payment order not found")))
                .flatMap(order -> {
                    if (order.getTxnType() != TransactionType.WITHDRAWAL) {
                        return Mono.error(new IllegalArgumentException("Order is not a withdrawal type"));
                    }

                    if (order.getStatus() != PaymentOrderStatus.AWAITING_APPROVAL) {
                        return Mono.error(new IllegalStateException("Order is not awaiting approval"));
                    }

                    // If rejected
                    if (!dto.isApprove()) {
                        order.setStatus(PaymentOrderStatus.REJECTED);
                        order.setReason(dto.getReason());
                        order.setApprovedBy(adminUserId);

                        return userProfileService.getUserProfileById(order.getUserId())
                                .flatMap(profile ->
                                        walletService.getWalletByUserProfileId(profile.getId())
                                                .flatMap(walletDto -> {
                                                    Wallet wallet = WalletMapper.toEntity(walletDto);
                                                    // move back the funds
                                                    wallet.setPendingWithdrawal(wallet.getPendingWithdrawal().subtract(order.getAmount()));
                                                    wallet.setTotalAvailableBalance(wallet.getTotalAvailableBalance().add(order.getAmount()));
                                                    wallet.setAvailableToWithdraw(wallet.getAvailableToWithdraw().add(order.getAmount()));
                                                    return walletService.saveWallet(wallet)
                                                            .then(orderRepo.save(order))
                                                            .map(PaymentOrderMapper::toDto);
                                                })
                                );
                    }

                    // If approved
                    order.setStatus(PaymentOrderStatus.COMPLETED);
                    order.setApprovedBy(adminUserId);
                    order.setUpdatedAt(Instant.now());
                    order.setReason(dto.getReason());

                    return userProfileService.getUserProfileById(order.getUserId())
                            .flatMap(profile ->
                                    walletService.getWalletByUserProfileId(profile.getId())
                                            .flatMap(walletDto -> {
                                                Wallet wallet = WalletMapper.toEntity(walletDto);
                                                // move from pendingWithdrawal → totalWithdrawal
                                                wallet.setPendingWithdrawal(wallet.getPendingWithdrawal().subtract(order.getAmount()));
                                                wallet.setTotalWithdrawal(wallet.getTotalWithdrawal().add(order.getAmount()));
                                                return walletService.saveWallet(wallet)
                                                        .then(transactionService.createTransaction(order))
                                                        .then(orderRepo.save(order))
                                                        .map(PaymentOrderMapper::toDto);
                                            })
                            );
                })
                .doOnSuccess(po -> log.info("Withdrawal {} successfully processed by admin {}", po.getTxnRef(), adminUserId))
                .doOnError(e -> log.error("Withdrawal approval failed: {}", e.getMessage(), e));

        return transactionalOperator.transactional(approvalFlow);
    }


//    @Override
//    public Mono<PaymentOrderDto> withdraw(WithdrawRequestDto withdrawRequestDto, Long telegramId) {
//        log.info("Initiating withdrawal for user: {}, amount: {}", telegramId, withdrawRequestDto.getAmount());
//
//        Mono<PaymentOrderDto> withdrawMono = userProfileService.getUserProfileByTelegramId(telegramId)
//                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found")))
//                .flatMap(userProfileDto ->
//                        walletService.getWalletByUserProfileId(userProfileDto.getId())
//                                .switchIfEmpty(
//                                        userProfileService.getUserProfileById(userProfileDto.getId())
//                                                .flatMap(profile -> walletService.createWallet(UserProfileMapper.toEntity(profile)))
//                                )
//                                .flatMap(walletDto -> {
//                                    Wallet wallet = WalletMapper.toEntity(walletDto);
//                                    BigDecimal amount = withdrawRequestDto.getAmount();
//
//                                    if (wallet.getAvailableToWithdraw() == null || amount == null) {
//                                        return Mono.error(new IllegalArgumentException("Invalid amount or wallet balance"));
//                                    }
//
//                                    if (wallet.getAvailableToWithdraw().compareTo(amount) < 0) {
//                                        return Mono.error(new RuntimeException("Insufficient withdrawable balance"));
//                                    }
//
//                                    wallet.setPendingWithdrawal(wallet.getPendingWithdrawal().add(amount));
//                                    wallet.setTotalAvailableBalance(wallet.getTotalAvailableBalance().subtract(amount));
//                                    wallet.setAvailableToWithdraw(wallet.getAvailableToWithdraw().subtract(amount));
//
//                                    PaymentOrder order = new PaymentOrder();
//                                    order.setUserId(userProfileDto.getId());
//                                    order.generateAndSetTxnRef();
//                                    order.setPhoneNumber(withdrawRequestDto.getPhoneNumber());
//                                    order.setAmount(amount);
//                                    order.setCurrency(withdrawRequestDto.getCurrency());
//                                    order.setStatus(PaymentOrderStatus.PENDING);
//                                    order.setReason("Withdrawal request awaiting admin approval");
//                                    order.setPaymentMethodId(withdrawRequestDto.getPaymentMethodId());
//                                    order.setTxnType(TransactionType.WITHDRAWAL);
//                                    order.setNonce(UUID.randomUUID().toString());
//
//                                    Map<String, String> metaData = new HashMap<>();
//                                    if (withdrawRequestDto.getBankName() != null)
//                                        metaData.put("bankName", withdrawRequestDto.getBankName());
//                                    if (withdrawRequestDto.getAccountName() != null)
//                                        metaData.put("accountName", withdrawRequestDto.getAccountName());
//                                    if (withdrawRequestDto.getAccountNumber() != null)
//                                        metaData.put("accountNumber", withdrawRequestDto.getAccountNumber());
//                                    if (withdrawRequestDto.getPhoneNumber() != null)
//                                        metaData.put("phoneNumber", withdrawRequestDto.getPhoneNumber());
//
//                                    try {
//                                        order.setMetaData(mapper.writeValueAsString(metaData));
//                                    } catch (JsonProcessingException e) {
//                                        return Mono.error(new RuntimeException("Failed to serialize withdrawal metadata", e));
//                                    }
//
//                                    PaymentMethod method = PaymentMethodMapper.toEntity(paymentMethod);
//                                    WithdrawalStrategy strategy = withdrawalStrategy.initiateWithdrawal(method);
//                                    return walletService.saveWallet(wallet)
//                                            .flatMap(savedWallet -> orderRepo.save(order))
//                                            .flatMap(savedOrder -> );
//                                })
//                )
//                .doOnSuccess(order -> log.info("Withdrawal request created successfully: {}", order))
//                .doOnError(e -> log.error("Failed to create withdrawal for user {}: {}", telegramId, e.getMessage(), e));
//
//        return transactionalOperator.transactional(withdrawMono);
//    }


    @Override
    public Mono<WithdrawalResponseDto> withdraw(WithdrawRequestDto withdrawRequestDto, Long telegramId) {
        log.info("Initiating withdrawal for user: {}, amount: {}", telegramId, withdrawRequestDto.getAmount());

        // Phase A: Load user -> create/get wallet -> create PaymentOrder (wallet not updated yet)
        Mono<Tuple2<PaymentOrder, UserProfileDto>> persistedOrderAndUserMono =
                userProfileService.getUserProfileByTelegramId(telegramId)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found")))
                        .flatMap(userProfileDto ->
                                getOrCreateWallet(userProfileDto)
                                        .flatMap(walletDto -> {
                                            BigDecimal amount = withdrawRequestDto.getAmount();
                                            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                                                return Mono.error(new IllegalArgumentException("Invalid withdrawal amount"));
                                            }

                                            Wallet wallet = WalletMapper.toEntity(walletDto);
                                            if (wallet.getAvailableToWithdraw() == null || wallet.getAvailableToWithdraw().compareTo(amount) < 0) {
                                                return Mono.error(new RuntimeException("Insufficient withdrawable balance"));
                                            }

                                            // Build PaymentOrder (wallet updates deferred)
                                            PaymentOrder order = buildPaymentOrder(userProfileDto, withdrawRequestDto, amount);

                                            // PENDING for offline, INITIATED for online
                                            if (withdrawRequestDto.getWithdrawalMode() == WithdrawalMode.ONLINE) {
                                                order.setStatus(PaymentOrderStatus.INITIATED);
                                            } else {
                                                order.setStatus(PaymentOrderStatus.PENDING);
                                            }

                                            return transactionalOperator.transactional(
                                                    orderRepo.save(order)
                                                            .map(savedOrder -> Tuples.of(savedOrder, userProfileDto))
                                            );
                                        })
                        );

        // Phase B: Call external provider -> update wallet + order after response
        return persistedOrderAndUserMono
                .flatMap(tuple -> {
                    PaymentOrder savedOrder = tuple.getT1();
                    UserProfileDto user = tuple.getT2();

                    return paymentMethodService.getPaymentMethodById(withdrawRequestDto.getPaymentMethodId())
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment method not found")))
                            .flatMap(paymentMethodDto -> {
                                PaymentMethod method = PaymentMethodMapper.toEntity(paymentMethodDto);
                                WithdrawalStrategy strategy = withdrawalStrategyFactory.getStrategy(withdrawRequestDto);

                                return strategy.initiateWithdrawal(savedOrder, method, withdrawRequestDto, user)
                                        .flatMap(resp -> {
                                            return transactionalOperator.transactional(
                                                    walletService.getWalletByUserProfileId(user.getId())
                                                            .flatMap(walletDto -> {
                                                                Wallet wallet = WalletMapper.toEntity(walletDto);
                                                                BigDecimal amount = withdrawRequestDto.getAmount();

                                                                // Online withdrawal completed
                                                                if (resp.getStatus() == PaymentOrderStatus.COMPLETED) {
                                                                    wallet.setTotalAvailableBalance(nonNullSubtract(wallet.getTotalAvailableBalance(), amount));
                                                                    wallet.setAvailableToWithdraw(nonNullSubtract(wallet.getAvailableToWithdraw(), amount));
                                                                    wallet.setPendingWithdrawal(nonNullSubtract(wallet.getPendingWithdrawal(), amount));
                                                                }
                                                                // PENDING or AWAITING_APPROVAL
                                                                else if (resp.getStatus() == PaymentOrderStatus.PENDING
                                                                        || resp.getStatus() == PaymentOrderStatus.AWAITING_APPROVAL
                                                                        || savedOrder.getStatus() == PaymentOrderStatus.PENDING) {
                                                                    wallet.setPendingWithdrawal(nonNullAdd(wallet.getPendingWithdrawal(), amount));
                                                                    wallet.setTotalAvailableBalance(nonNullSubtract(wallet.getTotalAvailableBalance(), amount));
                                                                    wallet.setAvailableToWithdraw(nonNullSubtract(wallet.getAvailableToWithdraw(), amount));
                                                                }
                                                                // FAILED
                                                                else if (resp.getStatus() == PaymentOrderStatus.FAILED) {
                                                                    savedOrder.setStatus(PaymentOrderStatus.FAILED);
                                                                }

                                                                // Update order with provider info
//                                                                savedOrder.setReason(resp.getMessage());
//                                                                if (resp.getProviderId() != null) {
//                                                                    savedOrder.setProviderOrderRef(resp.getProviderId());
//                                                                }

                                                                // Store provider response as metadata
                                                                try {
                                                                    Map<String, Object> metaFromProvider = mapper.convertValue(resp, Map.class);
                                                                    savedOrder.setMetaData(mapper.writeValueAsString(metaFromProvider));
                                                                } catch (JsonProcessingException e) {
                                                                    return Mono.error(new RuntimeException("Failed to serialize withdrawal metadata", e));
                                                                }

                                                                return walletService.saveWallet(wallet)
                                                                        .then(orderRepo.save(savedOrder))
                                                                        .map(updatedOrder -> resp);
                                                            })
                                            );
                                        })
                                        .onErrorResume(ex -> {
                                            // On error: mark order FAILED, wallet not updated
                                            savedOrder.setStatus(PaymentOrderStatus.FAILED);
                                            savedOrder.setReason(ex.getMessage());
                                            return transactionalOperator.transactional(
                                                    orderRepo.save(savedOrder)
                                            ).then(Mono.error(ex));
                                        });
                            });
                })
                .doOnSuccess(resp -> log.info("Withdrawal completed for user {} status={}", telegramId, resp.getStatus()))
                .doOnError(e -> log.error("Withdrawal failed for user {}: {}", telegramId, e.getMessage(), e));
    }



    /* ---------- Helper methods used above ---------- */

    /**
     * Create or return existing wallet DTO for the user.
     */
    private Mono<WalletDto> getOrCreateWallet(UserProfileDto userProfileDto) {
        return walletService.getWalletByUserProfileId(userProfileDto.getId())
                .switchIfEmpty(
                        walletService.createWallet(UserProfileMapper.toEntity(userProfileDto))
                                .doOnNext(w -> log.info("Created wallet for user {}", userProfileDto.getId()))
                );
    }

    /**
     * Build PaymentOrder and serialize initial metadata. Throws RuntimeException if serialization fails.
     */
    private PaymentOrder buildPaymentOrder(UserProfileDto userProfileDto, WithdrawRequestDto request, BigDecimal amount) {
        PaymentOrder order = new PaymentOrder();
        order.setUserId(userProfileDto.getId());
        order.generateAndSetTxnRef();
        order.setPhoneNumber(request.getPhoneNumber());
        order.setAmount(amount);
        order.setCurrency(request.getCurrency());
        order.setStatus(PaymentOrderStatus.PENDING);
        order.setReason("Withdrawal request");
        order.setPaymentMethodId(request.getPaymentMethodId());
        order.setTxnType(TransactionType.WITHDRAWAL);
        order.setNonce(UUID.randomUUID().toString());

        Map<String, String> metaData = new HashMap<>();
        if (request.getBankName() != null) metaData.put("bankName", request.getBankName());
        if (request.getAccountName() != null) metaData.put("accountName", request.getAccountName());
        if (request.getAccountNumber() != null) metaData.put("accountNumber", request.getAccountNumber());
        if (request.getPhoneNumber() != null) metaData.put("phoneNumber", request.getPhoneNumber());

        try {
            order.setMetaData(mapper.writeValueAsString(metaData));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize withdrawal metadata", e);
        }

        return order;
    }

    /**
     * Utility: add with null-safety
     */
    private BigDecimal nonNullAdd(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.add(b);
    }

    /**
     * Utility: subtract with null-safety
     */
    private BigDecimal nonNullSubtract(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.subtract(b);
    }


    @Override
    public Mono<PageResponse<PaymentOrderDto>> getAdminWithdrawals(String status, int page, int size) {
        int offset = (page - 1) * size;

        Flux<PaymentOrder> withdrawalsFlux;
        Mono<Long> countMono;

        if (status != null && !status.isEmpty()) {
            withdrawalsFlux = orderRepo.findAllWithdrawalsByStatus(status, size, offset);
            countMono = orderRepo.countAllWithdrawalsByStatus(status);
        } else {
            withdrawalsFlux = orderRepo.findAllWithdrawals(size, offset);
            countMono = orderRepo.countAllWithdrawals();
        }

        return withdrawalsFlux
                .map(PaymentOrderMapper::toDto)
                .collectList()
                .zipWith(countMono)
                .map(tuple -> new PageResponse<>(
                        tuple.getT1(),
                        page,
                        size,
                        tuple.getT2()
                ));
    }


    @Override
    public Mono<PageResponse<PaymentOrderDto>> getUserWithdrawals(Long userId, String status, int page, int size) {
        int offset = (page - 1) * size;

        Flux<PaymentOrder> withdrawalsFlux;
        Mono<Long> countMono;

        if (status != null && !status.isEmpty()) {
            withdrawalsFlux = orderRepo.findWithdrawalsByUserAndStatus(userId, status, size, offset);
            countMono = orderRepo.countUserWithdrawalsByStatus(userId, status);
        } else {
            withdrawalsFlux = orderRepo.findWithdrawalsByUser(userId, size, offset);
            countMono = orderRepo.countUserWithdrawals(userId);
        }

        return withdrawalsFlux
                .map(PaymentOrderMapper::toDto)
                .collectList()
                .zipWith(countMono)
                .map(tuple -> new PageResponse<>(
                        tuple.getT1(),
                        page,
                        size,
                        tuple.getT2()
                ));
    }


    @Override
    public Mono<PageResponse<PaymentOrderDto>> getAdminDeposits(String status, int page, int size) {
        int offset = (page - 1) * size;

        Flux<PaymentOrder> depositsFlux;
        Mono<Long> countMono;

        if (status != null && !status.isEmpty()) {
            depositsFlux = orderRepo.findAllDepositsByStatus(status, size, offset);
            countMono = orderRepo.countAllDepositsByStatus(status);
        } else {
            depositsFlux = orderRepo.findAllDeposits(size, offset);
            countMono = orderRepo.countAllDeposits();
        }

        return depositsFlux
                .map(PaymentOrderMapper::toDto)
                .collectList()
                .zipWith(countMono)
                .map(tuple -> new PageResponse<>(
                        tuple.getT1(),
                        page,
                        size,
                        tuple.getT2()
                ));
    }

    @Override
    public Mono<PageResponse<PaymentOrderDto>> getUserDeposits(Long userId, String status, int page, int size) {
        int offset = (page - 1) * size;

        Flux<PaymentOrder> depositsFlux;
        Mono<Long> countMono;

        if (status != null && !status.isEmpty()) {
            depositsFlux = orderRepo.findDepositsByUserAndStatus(userId, status, size, offset);
            countMono = orderRepo.countUserDepositsByStatus(userId, status);
        } else {
            depositsFlux = orderRepo.findDepositsByUser(userId, size, offset);
            countMono = orderRepo.countUserDeposits(userId);
        }

        return depositsFlux
                .map(PaymentOrderMapper::toDto)
                .collectList()
                .zipWith(countMono)
                .map(tuple -> new PageResponse<>(
                        tuple.getT1(),
                        page,
                        size,
                        tuple.getT2()
                ));
    }


}
