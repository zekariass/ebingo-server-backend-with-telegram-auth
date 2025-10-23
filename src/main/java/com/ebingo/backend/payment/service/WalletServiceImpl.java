package com.ebingo.backend.payment.service;

import com.ebingo.backend.payment.dto.WalletDto;
import com.ebingo.backend.payment.entity.Wallet;
import com.ebingo.backend.payment.enums.GameTxnType;
import com.ebingo.backend.payment.mappers.WalletMapper;
import com.ebingo.backend.payment.repository.WalletRepository;
import com.ebingo.backend.system.exceptions.InsufficientBalanceException;
import com.ebingo.backend.system.exceptions.ResourceNotFoundException;
import com.ebingo.backend.user.entity.UserProfile;
import com.ebingo.backend.user.mappers.UserProfileMapper;
import com.ebingo.backend.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final ReactiveTransactionManager transactionManager;
    private final UserProfileService userProfileService;

    @Override
    public Mono<WalletDto> createWallet(UserProfile userProfile) {
        log.info("Creating wallet for user with profile: {}", UserProfileMapper.toDto(userProfile));

        Wallet wallet = new Wallet();
        wallet.setUserProfileId(userProfile.getId());

        // Use reactive transaction operator
        TransactionalOperator operator = TransactionalOperator.create(transactionManager);

        return walletRepository.save(wallet)
                .doOnNext(savedWallet -> log.info("Wallet created with id: {}", savedWallet.getId()))
                .map(WalletMapper::toDto)
                .as(operator::transactional);
    }

    @Override
    public Mono<WalletDto> getWalletByUserProfileId(Long userProfileId) {
        log.info("Getting wallet by user profile id: {}", userProfileId);
        return walletRepository.findByUserProfileId(userProfileId)
                .map(WalletMapper::toDto);
    }

    @Override
    public Mono<WalletDto> getWalletByTelegramId(Long telegramId) {
        log.info("Getting wallet by user telegram id: {}", telegramId);

        return userProfileService.getUserProfileByTelegramId(telegramId)
                .flatMap(up -> walletRepository.findByUserProfileId(up.getId()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Wallet not found for user with telegram id: " + telegramId)))
                .map(WalletMapper::toDto);
    }

    @Override
    public Mono<WalletDto> saveWallet(Wallet wallet) {
        log.info("Saving wallet with id: {}", wallet.getId());

        return walletRepository.save(wallet)
                .flatMap(savedWallet -> {
                    log.info("Wallet saved with id: {}", savedWallet.getId());
                    return Mono.just(WalletMapper.toDto(savedWallet));
                });
    }

    @Override
    public Mono<WalletDto> debit(Wallet wallet, BigDecimal amount, GameTxnType gameTxnType) {
        log.info("Debiting wallet with id: {} for amount: {}", wallet.getId(), amount);
        if (GameTxnType.GAME_FEE.equals(gameTxnType)) {

            if (wallet.getTotalAvailableBalance().compareTo(amount) < 0) {
                return Mono.error(new InsufficientBalanceException("Insufficient balance to cover game fee"));
            }

            BigDecimal remaining = amount;

            // 1️⃣ Track how much comes from each source (for logs/clarity)
            BigDecimal usedFromWelcome = BigDecimal.ZERO;
            BigDecimal usedFromReferral = BigDecimal.ZERO;

            // 2️⃣ Debit from available welcome bonus
            if (wallet.getAvailableWelcomeBonus().compareTo(BigDecimal.ZERO) > 0) {
                usedFromWelcome = wallet.getAvailableWelcomeBonus().min(remaining);
                wallet.setAvailableWelcomeBonus(wallet.getAvailableWelcomeBonus().subtract(usedFromWelcome));
                wallet.setWelcomeBonus(wallet.getWelcomeBonus().subtract(usedFromWelcome));
                remaining = remaining.subtract(usedFromWelcome);
            }

            // 3️⃣ Debit from available referral bonus
            if (remaining.compareTo(BigDecimal.ZERO) > 0 && wallet.getAvailableReferralBonus().compareTo(BigDecimal.ZERO) > 0) {
                usedFromReferral = wallet.getAvailableReferralBonus().min(remaining);
                wallet.setAvailableReferralBonus(wallet.getAvailableReferralBonus().subtract(usedFromReferral));
                wallet.setReferralBonus(wallet.getReferralBonus().subtract(usedFromReferral));
                remaining = remaining.subtract(usedFromReferral);
            }

            // 4️⃣ Finally, deduct the full amount from totalAvailableBalance (because it includes all bonuses)
            wallet.setTotalAvailableBalance(wallet.getTotalAvailableBalance().subtract(amount));

            log.info("✅ Debit complete for wallet {} (welcome used: {}, referral used: {}, remaining cash: {})",
                    wallet.getId(), usedFromWelcome, usedFromReferral, remaining);

            return walletRepository.save(wallet)
                    .map(WalletMapper::toDto);
        }


        // Default case for other transaction types
        return Mono.just(WalletMapper.toDto(wallet));
    }

    @Override
    public Mono<WalletDto> credit(Wallet wallet, BigDecimal amount, GameTxnType gameTxnType) {
        if (GameTxnType.REFUND.equals(gameTxnType) || GameTxnType.PRIZE_PAYOUT.equals(gameTxnType)) {

            // Recalculate totalAvailableBalance and availableToWithdraw
            BigDecimal totalAvailableBalance = wallet.getTotalAvailableBalance().add(amount);

            if (GameTxnType.PRIZE_PAYOUT.equals(gameTxnType)) {
                wallet.setTotalPrizeAmount(wallet.getTotalPrizeAmount().add(amount));
            }

            // Usually, only deposit is withdrawable
            BigDecimal availableToWithdraw = totalAvailableBalance.subtract(wallet.getAvailableWelcomeBonus()).subtract(wallet.getAvailableReferralBonus());

            wallet.setTotalAvailableBalance(totalAvailableBalance);
            wallet.setAvailableToWithdraw(availableToWithdraw);

            // Save and return updated DTO
            return walletRepository.save(wallet)
                    .map(WalletMapper::toDto);
        }

        return Mono.error(new IllegalArgumentException("Unsupported transaction type for credit: " + gameTxnType));
    }


}
