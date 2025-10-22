package com.ebingo.backend.payment.service;

import com.ebingo.backend.payment.dto.InitiateDepositRequest;
import com.ebingo.backend.payment.dto.TransactionDto;
import com.ebingo.backend.payment.dto.WithdrawRequestDto;
import com.ebingo.backend.payment.entity.Wallet;
import com.ebingo.backend.payment.enums.TransactionStatus;
import com.ebingo.backend.payment.enums.TransactionType;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface TransactionService {
    Flux<TransactionDto> getPaginatedTransaction(Long telegramId, Integer page, Integer size, String sortBy);

    Mono<TransactionDto> getTransactionById(Long id, Long telegramId);

    Mono<TransactionDto> initiateOfflineDeposit(InitiateDepositRequest depositRequest, Long telegramId);

    Mono<TransactionDto> confirmDepositOfflineByAdmin(String txnRef, String metaData, Long approverTelegramId);

    Flux<TransactionDto> getPaginatedDepositsByStatusForAdmin(TransactionStatus status, TransactionType type, Integer page, Integer size, String sortBy);

    Mono<TransactionDto> cancelDepositOffline(Long id);

    Mono<TransactionDto> withdraw(@Valid WithdrawRequestDto withdrawRequestDto, Long telegramId);

    Mono<TransactionDto> confirmWithdrawalByAdmin(String txnRef, Long telegramId);

    Mono<TransactionDto> rejectWithdrawalByAdmin(String txnRef, String reason, Long approverTelegramId);

    Mono<Wallet> debitReceiverWalletBalanceForDepositReversal(Wallet wallet, BigDecimal amount);

    Mono<TransactionDto> changeTransactionStatus(String txnRef, TransactionStatus status, Long approverTelegramId);

//    Flux<TransactionDto> getPaginatedTransactionByTypeForAdmin(TransactionType type, Integer page, Integer size, String sortBy);
}
