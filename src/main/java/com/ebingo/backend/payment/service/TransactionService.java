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
    Flux<TransactionDto> getPaginatedTransaction(String phoneNumber, Integer page, Integer size, String sortBy);

    Mono<TransactionDto> getTransactionById(Long id, String userSupabaseId);

    Mono<TransactionDto> initiateOfflineDeposit(InitiateDepositRequest depositRequest, String userSupabaseId);

    Mono<TransactionDto> confirmDepositOfflineByAdmin(String txnRef, String metaData, String approverSupabaseId);

    Flux<TransactionDto> getPaginatedDepositsByStatusForAdmin(TransactionStatus status, TransactionType type, Integer page, Integer size, String sortBy);

    Mono<TransactionDto> cancelDepositOffline(Long id);

    Mono<TransactionDto> withdraw(@Valid WithdrawRequestDto withdrawRequestDto, String userSupabaseId);

    Mono<TransactionDto> confirmWithdrawalByAdmin(String txnRef, String approverSupabaseId);

    Mono<TransactionDto> rejectWithdrawalByAdmin(String txnRef, String reason, String approverSupabaseId);

    Mono<Wallet> debitReceiverWalletBalanceForDepositReversal(Wallet wallet, BigDecimal amount);

    Mono<TransactionDto> changeTransactionStatus(String txnRef, TransactionStatus status, String approverSupabaseId);

//    Flux<TransactionDto> getPaginatedTransactionByTypeForAdmin(TransactionType type, Integer page, Integer size, String sortBy);
}
