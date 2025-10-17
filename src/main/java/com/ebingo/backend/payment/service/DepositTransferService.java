package com.ebingo.backend.payment.service;

import com.ebingo.backend.payment.dto.DepositTransferDto;
import com.ebingo.backend.payment.dto.DepositTransferRequestDto;
import com.ebingo.backend.payment.entity.Wallet;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface DepositTransferService {
    Flux<DepositTransferDto> getPaginatedDepositTransfer(String userSupabaseId, Integer page, Integer size, String sortBy);

    Mono<DepositTransferDto> getASingleDepositTransfer(Long id, String userSupabaseId);

    Mono<Void> deleteDepositTransfer(Long id, String userSupabaseId);

    Mono<DepositTransferDto> createDepositTransfer(@Valid DepositTransferRequestDto depositTransferDto, String userSupabaseId);

    Mono<Wallet> creditReceiverWalletBalanceForDeposit(Wallet receiverWallet, BigDecimal amount);
}
