package com.ebingo.backend.payment.service;

import com.ebingo.backend.payment.dto.InitiateDepositRequest;
import com.ebingo.backend.payment.dto.TransactionDto;
import com.ebingo.backend.payment.dto.WithdrawRequestDto;
import com.ebingo.backend.payment.entity.Transaction;
import com.ebingo.backend.payment.entity.Wallet;
import com.ebingo.backend.payment.enums.TransactionStatus;
import com.ebingo.backend.payment.enums.TransactionType;
import com.ebingo.backend.payment.mappers.TransactionMapper;
import com.ebingo.backend.payment.mappers.WalletMapper;
import com.ebingo.backend.payment.repository.TransactionRepository;
import com.ebingo.backend.system.exceptions.ResourceNotFoundException;
import com.ebingo.backend.user.dto.UserProfileDto;
import com.ebingo.backend.user.mappers.UserProfileMapper;
import com.ebingo.backend.user.service.UserProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserProfileService userProfileService;
    private final WalletService walletService;
    private final TransactionalOperator transactionalOperator;
    private final DepositTransferService depositTransferService;
    private final ObjectMapper objectMapper;


    @Override
    public Flux<TransactionDto> getPaginatedTransaction(Long telegramId, Integer page, Integer size, String sortBy) {
        int pageNumber = (page != null && page >= 1) ? page : 1;
        int pageSize = (size != null && size > 0 && size <= 100) ? size : 10;
        long offset = (long) (pageNumber - 1) * pageSize;

        return userProfileService.getUserProfileByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found")))
                .flatMapMany(up -> {
                    String sortKey = (sortBy != null) ? sortBy.toLowerCase() : "id";
                    return switch (sortKey) {
                        case "txnamount" -> transactionRepository
                                .findByPlayerIdOrderByTxnAmountDesc(up.getId(), pageSize, offset);
                        case "createdat" -> transactionRepository
                                .findByPlayerIdOrderByCreatedAtDesc(up.getId(), pageSize, offset);
                        default -> transactionRepository
                                .findByPlayerIdOrderByIdDesc(up.getId(), pageSize, offset);
                    };
                })
                .map(TransactionMapper::toDto)
                .doOnSubscribe(s -> log.info("Fetching transactions - Page: {}, Size: {}, SortBy: {}", page, size, sortBy))
                .doOnComplete(() -> log.info("Completed fetching transactions for user: {}", telegramId))
                .doOnError(e -> log.error("Failed to fetch transactions: {}", e.getMessage(), e));
    }


    @Override
    public Mono<TransactionDto> getTransactionById(Long id, Long phoneNumber) {
        log.info("Fetching transaction by ID: {}", id);

        return userProfileService.getUserProfileByTelegramId(phoneNumber)
                .flatMap(up ->
                        transactionRepository.findByIdAndPlayerId(id, up.getId())
                                .switchIfEmpty(Mono.error(
                                        new RuntimeException("Transaction not found with id: " + id + " for user: " + up.getId())
                                ))
                                .map(TransactionMapper::toDto)
                );
    }


    @Override
    public Mono<TransactionDto> initiateOfflineDeposit(InitiateDepositRequest depositRequest, Long phoneNumber) {
        log.info("Initiating deposit: {}", depositRequest);

        return userProfileService.getUserProfileByTelegramId(phoneNumber)
                .flatMap(up -> {
                    Transaction txn = TransactionMapper.toEntity(depositRequest, up);
                    txn.setTxnRef(generateTxnRef());
                    return transactionRepository.save(txn);
                })
                .map(TransactionMapper::toDto)
                .doOnSuccess(txn -> log.info("Deposit initiated successfully: {}", txn))
                .doOnError(e -> log.error("Failed to initiate deposit: {}", e.getMessage(), e));
    }


//    @Override
//    public Mono<TransactionDto> confirmDepositOfflineByAdmin(String txnRef, String metaData, String approverPhoneNumber) {
//        Mono<UserProfileDto> approver = userProfileService.getUserProfileBySupabaseId(approverPhoneNumber);
//
//        Mono<TransactionDto> txMono = transactionRepository.findByTxnRef(txnRef)
//                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Transaction not found")))
//                .flatMap(txn -> {
//                    txn.setStatus(TransactionStatus.COMPLETED);
//                    txn.setMetaData(metaData);
//
//                    return approver.flatMap(up -> {
//                                txn.setApprovedBy(up.getId());
//                                txn.setApprovedAt(Instant.now());
//                                return Mono.just(txn);
//                            }).then(transactionRepository.save(txn))
//                            .flatMap(savedTxn ->
//                                    walletService.getWalletByUserProfileId(savedTxn.getPlayerId())
//                                            // If wallet does not exist, create it
//                                            .switchIfEmpty(
//                                                    userProfileService.getUserProfileById(savedTxn.getPlayerId())
//                                                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Player profile not found")))
//                                                            .flatMap(profile -> walletService.createWallet(UserProfileMapper.toEntity(profile)))
//                                            )
//                                            // Update wallet using helper
//                                            .flatMap(walletDto -> {
//                                                Wallet wallet = WalletMapper.toEntity(walletDto);
//                                                return depositTransferService.creditReceiverWalletBalanceForDeposit(wallet, savedTxn.getTxnAmount())
//                                                        .flatMap(walletService::saveWallet)
//                                                        .thenReturn(savedTxn);
//                                            })
//                            );
//                })
//                .map(TransactionMapper::toDto);
//
//        // Wrap in reactive transaction
//        return transactionalOperator.transactional(txMono)
//                .doOnSubscribe(s -> log.info("Confirming offline deposit: {}", txnRef))
//                .doOnSuccess(tx -> log.info("Deposit confirmed successfully: {}", tx))
//                .doOnError(e -> log.error("Failed to confirm deposit offline: {}", e.getMessage(), e));
//    }


    @Override
    public Mono<TransactionDto> confirmDepositOfflineByAdmin(
            String txnRef, String metaData, Long phoneNumber) {

        // Get approver info
        Mono<UserProfileDto> approverMono = userProfileService.getUserProfileByTelegramId(phoneNumber);

        // Wrap the whole flow in transactional operator
        return transactionalOperator.transactional(

                        transactionRepository.findByTxnRef(txnRef)
                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Transaction not found")))
                                .flatMap(txn ->
                                        approverMono.flatMap(approver -> {
                                            txn.setStatus(TransactionStatus.COMPLETED);
                                            txn.setMetaData(metaData);
                                            txn.setApprovedBy(approver.getId());
                                            txn.setApprovedAt(Instant.now());
                                            return transactionRepository.save(txn);
                                        })
                                )
                                .flatMap(savedTxn -> {
                                    Long playerId = savedTxn.getPlayerId();

                                    return walletService.getWalletByUserProfileId(playerId)
                                            .switchIfEmpty(
                                                    userProfileService.getUserProfileById(playerId)
                                                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Player profile not found")))
                                                            .flatMap(profile -> walletService.createWallet(UserProfileMapper.toEntity(profile)))
                                            )
                                            .flatMap(walletDto -> {
                                                Wallet wallet = WalletMapper.toEntity(walletDto);
                                                return depositTransferService
                                                        .creditReceiverWalletBalanceForDeposit(wallet, savedTxn.getTxnAmount())
                                                        .flatMap(walletService::saveWallet)
                                                        .thenReturn(savedTxn);
                                            });
                                })
                                .map(TransactionMapper::toDto)
                )
                .doOnSubscribe(s -> log.info("Confirming offline deposit: {}", txnRef))
                .doOnSuccess(tx -> log.info("Deposit confirmed successfully: {}", tx))
                .doOnError(e -> log.error("Failed to confirm deposit offline: {}", e.getMessage(), e));
    }


    @Override
    public Flux<TransactionDto> getPaginatedDepositsByStatusForAdmin(TransactionStatus status, TransactionType type, Integer page, Integer size, String sortBy) {
        int pageNumber = (page != null && page >= 1) ? page : 1;
        int pageSize = (size != null && size > 0 && size <= 100) ? size : 10;
        long offset = (long) (pageNumber - 1) * pageSize;

        String sortKey = (sortBy != null) ? sortBy.toLowerCase() : "id";

        Flux<Transaction> txnFlux = switch (sortKey) {
            case "txnamount" ->
                    transactionRepository.findByStatusAndTxnTypeOrderByTxnAmountDesc(status.name(), type.name(), pageSize, offset);
            case "createdat" ->
                    transactionRepository.findByStatusAndTxnTypeOrderByCreatedAtDesc(status.name(), type.name(), pageSize, offset);
            default ->
                    transactionRepository.findByStatusAndTxnTypeOrderByIdDesc(status.name(), type.name(), pageSize, offset);
        };

        return txnFlux
                .map(TransactionMapper::toDto)
                .doOnSubscribe(s -> log.info("Fetching transactions by status: {}, Page: {}, Size: {}, SortBy: {}", status, page, size, sortBy))
                .doOnComplete(() -> log.info("Completed fetching transactions by status: {}", status))
                .doOnError(e -> log.error("Failed to fetch transactions by status {}: {}", status, e.getMessage(), e));
    }

    @Override
    public Mono<TransactionDto> cancelDepositOffline(Long id) {
        return transactionRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Transaction not found")))
                .flatMap(txn -> {
                    txn.setStatus(TransactionStatus.CANCELLED);
                    return transactionRepository.save(txn);
                })
                .map(TransactionMapper::toDto)
                .doOnSubscribe(s -> log.info("Canceling deposit: {}", id))
                .doOnSuccess(txn -> log.info("Deposit canceled successfully: {}", txn))
                .doOnError(e -> log.error("Failed to cancel deposit: {}", e.getMessage(), e));
    }


    @Override
    public Mono<TransactionDto> withdraw(WithdrawRequestDto withdrawRequestDto, Long telegramId) {
        log.info("Initiating withdrawal for user: {}, amount: {}", telegramId, withdrawRequestDto.getAmount());

        Mono<TransactionDto> withdrawMono = userProfileService.getUserProfileByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found")))
                .flatMap(userProfileDto ->
                        walletService.getWalletByUserProfileId(userProfileDto.getId())
                                .switchIfEmpty(
                                        userProfileService.getUserProfileById(userProfileDto.getId())
                                                .flatMap(profile -> walletService.createWallet(UserProfileMapper.toEntity(profile)))
                                )
                                .flatMap(walletDto -> {
                                    Wallet wallet = WalletMapper.toEntity(walletDto);
                                    BigDecimal amount = withdrawRequestDto.getAmount();

                                    if (wallet.getAvailableToWithdraw().compareTo(amount) < 0 &&
                                            wallet.getTotalAvailableBalance().compareTo(amount) < 0) {
                                        return Mono.error(new RuntimeException("Insufficient withdrawable balance"));
                                    }

                                    // Mark amount as pending withdrawal
                                    wallet.setPendingWithdrawal(wallet.getPendingWithdrawal().add(amount));
                                    wallet.setTotalAvailableBalance(wallet.getTotalAvailableBalance().subtract(amount));
                                    wallet.setAvailableToWithdraw(wallet.getAvailableToWithdraw().subtract(amount));

                                    Transaction txn = new Transaction();
                                    txn.setTxnRef(generateTxnRef());
                                    txn.setPaymentMethodId(withdrawRequestDto.getPaymentMethodId());
                                    txn.setPlayerId(userProfileDto.getId());
                                    txn.setTxnAmount(amount);
                                    txn.setTxnType(TransactionType.WITHDRAWAL);
                                    txn.setStatus(TransactionStatus.PENDING);
                                    txn.setCreatedAt(Instant.now());

                                    Map<String, String> metaData = new HashMap<>();
                                    metaData.put("accountNumber", withdrawRequestDto.getAccountNumber());
                                    metaData.put("bankName", withdrawRequestDto.getBankName());
                                    metaData.put("accountName", withdrawRequestDto.getAccountName());

                                    try {
                                        txn.setMetaData(objectMapper.writeValueAsString(metaData));
                                    } catch (JsonProcessingException e) {
                                        throw new RuntimeException(e);
                                    }

                                    return walletService.saveWallet(wallet)
                                            .then(transactionRepository.save(txn));
                                })
                )
                .map(TransactionMapper::toDto)
                .doOnSuccess(txn -> log.info("Withdrawal request created successfully: {}", txn))
                .doOnError(e -> log.error("Failed to create withdrawal: {}", e.getMessage(), e));

        return transactionalOperator.transactional(withdrawMono);
    }


    @Override
    public Mono<TransactionDto> confirmWithdrawalByAdmin(String txnRef, Long approverPhoneNumber) {
        log.info("Admin confirming withdrawal for txnRef: {}", txnRef);

        Mono<UserProfileDto> approver = userProfileService.getUserProfileByTelegramId(approverPhoneNumber);

        Mono<TransactionDto> txMono = transactionRepository.findByTxnRef(txnRef)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Transaction not found")))
                .flatMap(txn -> {
                    if (!TransactionType.WITHDRAWAL.equals(txn.getTxnType())) {
                        return Mono.error(new RuntimeException("Transaction is not a withdrawal"));
                    }
                    if (txn.getStatus() == TransactionStatus.COMPLETED) {
                        return Mono.error(new RuntimeException("Withdrawal already completed"));
                    }

//                    txn.setMetaData(metaData);
                    txn.setStatus(TransactionStatus.COMPLETED);

                    return approver.flatMap(admin -> {
                                txn.setApprovedBy(admin.getId());
                                txn.setApprovedAt(Instant.now());
                                return Mono.just(txn);
                            })
                            .then(transactionRepository.save(txn))
                            .flatMap(savedTxn ->
                                            walletService.getWalletByUserProfileId(savedTxn.getPlayerId())
                                                    .switchIfEmpty(Mono.error(new ResourceNotFoundException("Wallet not found")))
                                                    .flatMap(walletDto -> {
                                                        Wallet wallet = WalletMapper.toEntity(walletDto);
                                                        BigDecimal amount = savedTxn.getTxnAmount();
                                                        savedTxn.setStatus(TransactionStatus.COMPLETED);

                                                        // Validate sufficient balance
                                                        if (wallet.getPendingWithdrawal().compareTo(amount) < 0) {
                                                            return Mono.error(new RuntimeException("Insufficient pending withdrawal balance"));
                                                        }

                                                        // Deduct appropriately
//                                                wallet.setAvailableToWithdraw(wallet.getAvailableToWithdraw().subtract(amount));
//                                                wallet.setTotalAvailableBalance(wallet.getTotalAvailableBalance().subtract(amount));
                                                        wallet.setPendingWithdrawal(wallet.getPendingWithdrawal().subtract(amount));

                                                        // If withdrawal was previously pending, reduce pendingWithdrawal
                                                        if (wallet.getPendingWithdrawal() != null && wallet.getPendingWithdrawal().compareTo(BigDecimal.ZERO) > 0) {
                                                            BigDecimal pendingAfter = wallet.getPendingWithdrawal().subtract(amount);
                                                            wallet.setPendingWithdrawal(pendingAfter.max(BigDecimal.ZERO));
                                                        }

                                                        wallet.setUpdatedAt(Instant.now());

                                                        return walletService.saveWallet(wallet)
                                                                .then(transactionRepository.save(savedTxn));
                                                    })
                            );
                })
                .map(TransactionMapper::toDto)
                .doOnSuccess(tx -> log.info("Withdrawal confirmed successfully: {}", tx))
                .doOnError(e -> log.error("Failed to confirm withdrawal: {}", e.getMessage(), e));

        return transactionalOperator.transactional(txMono);
    }


    @Override
    public Mono<TransactionDto> rejectWithdrawalByAdmin(String txnRef, String reason, Long approverPhoneNumber) {
        log.info("Admin rejecting withdrawal for txnRef: {}", txnRef);

        Mono<UserProfileDto> approver = userProfileService.getUserProfileByTelegramId(approverPhoneNumber);

        Mono<TransactionDto> txMono = transactionRepository.findByTxnRef(txnRef)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Transaction not found")))
                .flatMap(txn -> {
                    if (!TransactionType.WITHDRAWAL.equals(txn.getTxnType())) {
                        return Mono.error(new RuntimeException("Transaction is not a withdrawal"));
                    }
                    if (txn.getStatus() == TransactionStatus.COMPLETED || txn.getStatus() == TransactionStatus.CANCELLED) {
                        return Mono.error(new RuntimeException("Withdrawal already processed"));
                    }

                    txn.setStatus(TransactionStatus.REJECTED);
                    txn.setMetaData(reason);

                    return approver.flatMap(admin -> {
                                txn.setApprovedBy(admin.getId());
                                txn.setApprovedAt(Instant.now());
                                return Mono.just(txn);
                            })
                            .then(transactionRepository.save(txn))
                            .flatMap(savedTxn ->
                                    walletService.getWalletByUserProfileId(savedTxn.getPlayerId())
                                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Wallet not found")))
                                            .flatMap(walletDto -> {
                                                Wallet wallet = WalletMapper.toEntity(walletDto);
                                                BigDecimal amount = savedTxn.getTxnAmount();

                                                // Reverse pending withdrawal (return funds to availability)
                                                if (wallet.getPendingWithdrawal().compareTo(amount) >= 0) {
                                                    wallet.setPendingWithdrawal(wallet.getPendingWithdrawal().subtract(amount));
                                                    wallet.setTotalAvailableBalance(wallet.getTotalAvailableBalance().add(amount));
                                                    wallet.setAvailableToWithdraw(wallet.getAvailableToWithdraw().add(amount));
                                                } else {
                                                    wallet.setPendingWithdrawal(BigDecimal.ZERO);
                                                }

                                                // Funds remain in availableToWithdraw
                                                wallet.setUpdatedAt(Instant.now());

                                                return walletService.saveWallet(wallet)
                                                        .thenReturn(savedTxn);
                                            })
                            );
                })
                .map(TransactionMapper::toDto)
                .doOnSuccess(tx -> log.info("Withdrawal rejected successfully: {}", tx))
                .doOnError(e -> log.error("Failed to reject withdrawal: {}", e.getMessage(), e));

        return transactionalOperator.transactional(txMono);
    }


    @Override
    public Mono<Wallet> debitReceiverWalletBalanceForDepositReversal(Wallet wallet, BigDecimal amount) {
        return Mono.fromSupplier(() -> {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Reversal amount must be greater than zero");
            }

            // Determine how much can actually be deducted without going below zero
            BigDecimal available = wallet.getTotalAvailableBalance();
            BigDecimal withdrawable = wallet.getAvailableToWithdraw();
            BigDecimal totalDeposit = wallet.getTotalDeposit();

            // The effective reversal cannot exceed the current available or deposited amount
            BigDecimal effectiveAmount = amount.min(available).min(withdrawable).min(totalDeposit);

            if (effectiveAmount.compareTo(BigDecimal.ZERO) == 0) {
                // Nothing to reverse
                return wallet;
            }

            // Reduce deposit and balances
            wallet.setTotalDeposit(
                    wallet.getTotalDeposit().subtract(effectiveAmount).max(BigDecimal.ZERO)
            );

            wallet.setTotalAvailableBalance(
                    wallet.getTotalAvailableBalance().subtract(effectiveAmount).max(BigDecimal.ZERO)
            );

            wallet.setAvailableToWithdraw(
                    wallet.getAvailableToWithdraw().subtract(effectiveAmount).max(BigDecimal.ZERO)
            );

            // Optionally record total withdrawal for tracking corrections
            wallet.setTotalWithdrawal(
                    wallet.getTotalWithdrawal().add(effectiveAmount)
            );

            wallet.setUpdatedAt(Instant.now());

            return wallet;
        });
    }


    @Override
    public Mono<TransactionDto> changeTransactionStatus(String txnRef, TransactionStatus status, Long approverTelegramId) {
        return transactionRepository.findByTxnRef(txnRef)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Transaction not found")))
                .flatMap(txn -> {
                    txn.setStatus(status);
                    if (status == TransactionStatus.COMPLETED) {
                        txn.setApprovedAt(Instant.now());

                        if (txn.getTxnType() == TransactionType.WITHDRAWAL) {
                            return confirmWithdrawalByAdmin(txnRef, approverTelegramId);
                        } else if (txn.getTxnType() == TransactionType.DEPOSIT) {
                            return confirmDepositOfflineByAdmin(txnRef, txn.getMetaData(), approverTelegramId);
                        }

                    } else if (status == TransactionStatus.REJECTED && txn.getTxnType() == TransactionType.WITHDRAWAL) {
                        return rejectWithdrawalByAdmin(txnRef, "Rejected by admin", approverTelegramId);
                    } else if (status == TransactionStatus.CANCELLED && txn.getTxnType() == TransactionType.DEPOSIT) {
                        return cancelDepositOffline(txn.getId());
                    }

                    // Ensure consistent Mono<TransactionDto> return type
                    return transactionRepository.save(txn)
                            .map(TransactionMapper::toDto);
                })
                .doOnSubscribe(s -> log.info("Changing transaction status for {}: {}", txnRef, status))
                .doOnSuccess(txn -> log.info("Transaction status changed successfully: {}", txn))
                .doOnError(e -> log.error("Failed to change transaction status: {}", e.getMessage(), e));
    }


    private String generateTxnRef() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
