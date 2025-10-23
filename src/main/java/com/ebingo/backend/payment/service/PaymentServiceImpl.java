package com.ebingo.backend.payment.service;

import com.ebingo.backend.payment.enums.GameTxnType;
import com.ebingo.backend.payment.repository.WalletRepository;
import com.ebingo.backend.system.exceptions.ResourceNotFoundException;
import com.ebingo.backend.user.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;


@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final UserProfileService userProfileService;
    private final GameTransactionService gameTxnService;

    public PaymentServiceImpl(WalletRepository walletRepository, WalletService walletService, UserProfileService userProfileService, GameTransactionService gameTxnService) {
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.userProfileService = userProfileService;
        this.gameTxnService = gameTxnService;
    }

//    @Override
//    public Mono<Boolean> processPayment(Long telegramId, BigDecimal amount, Long gameId) {
//        return userProfileService.getUserProfileByTelegramId(telegramId)
//                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found")))
//                .flatMap(userProfile ->
//                        walletRepository.findByUserProfileId(userProfile.getId())
//                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Wallet not found")))
//                                .flatMap(wallet ->
//                                        walletService.debit(wallet, amount, GameTxnType.GAME_FEE)
//                                                .then(gameTxnService.createGameTransaction(
//                                                        userProfile.getId(),
//                                                        amount,
//                                                        GameTxnType.GAME_FEE,
//                                                        gameId
//                                                ))
//                                                .thenReturn(true)
//                                )
//                )
//                .onErrorResume(ex -> {
//                    log.error("Payment processing failed for gameId={}: {}", gameId, ex.getMessage());
//                    return Mono.just(false);
//                });
//    }


    @Override
    public Mono<Boolean> processPayment(Long telegramId, BigDecimal amount, Long gameId) {
        return userProfileService.getUserProfileByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found")))
                .flatMap(userProfile -> gameTxnService.createGameTransaction(
                                        userProfile.getId(),
                                        amount,
                                        GameTxnType.GAME_FEE,
                                        gameId
                                )
                                .thenReturn(true)
                )
                .onErrorResume(ex -> {
                    log.error("Payment processing failed for gameId={}: {}", gameId, ex.getMessage());
                    return Mono.just(false);
                });
    }


    @Override
    public Mono<Boolean> processRefund(Long telegramId, Long gameId) {
        return userProfileService.getUserProfileByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found")))
                .flatMap(userProfile ->
                        gameTxnService.getTransactionByUserIdAndGameId(userProfile.getId(), gameId, GameTxnType.GAME_FEE)
                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Original game transaction not found")))
                                .flatMap(originalTxn ->
                                        walletRepository.findByUserProfileId(userProfile.getId())
                                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Wallet not found")))
                                                .flatMap(wallet ->
                                                        walletService.credit(wallet, originalTxn.getTxnAmount(), GameTxnType.REFUND)
                                                                .then(gameTxnService.createGameTransaction(
                                                                        userProfile.getId(),
                                                                        originalTxn.getTxnAmount(),
                                                                        GameTxnType.REFUND,
                                                                        gameId
                                                                ))
                                                                .thenReturn(true)
                                                )
                                )
                )
                // Handle any error gracefully — return false instead of propagating
                .onErrorResume(ex -> {
                    log.error("Refund processing failed for gameId={}: {}", gameId, ex.getMessage());
                    return Mono.just(false);
                });
    }

}
