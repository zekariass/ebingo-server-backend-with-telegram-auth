package com.ebingo.backend.payment.service;

import com.ebingo.backend.payment.dto.WalletDto;
import com.ebingo.backend.payment.entity.Wallet;
import com.ebingo.backend.payment.enums.GameTxnType;
import com.ebingo.backend.payment.enums.TransactionType;
import com.ebingo.backend.user.entity.UserProfile;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

public interface WalletService {
    Mono<WalletDto> createWallet(UserProfile userProfile);

    Mono<WalletDto> getWalletByUserProfileId(Long userProfileId);

    Mono<WalletDto> getWalletByTelegramId(Long telegramId);

    Mono<WalletDto> saveWallet(Wallet wallet);

    Mono<WalletDto> debit(Wallet wallet, BigDecimal amount, GameTxnType gameTxnType);

    Mono<WalletDto> credit(Wallet wallet, BigDecimal amount, GameTxnType gameTxnType);

    Mono<WalletDto> credit(Long userProfileId, BigDecimal amount, String reason, TransactionType transactionType, Map<String, Object> metadata);
}
