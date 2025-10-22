package com.ebingo.backend.payment.service;

import com.ebingo.backend.game.repository.RoomRepository;
import com.ebingo.backend.game.state.GameState;
import com.ebingo.backend.payment.dto.GameTransactionDto;
import com.ebingo.backend.payment.entity.GameTransaction;
import com.ebingo.backend.payment.enums.GameTxnStatus;
import com.ebingo.backend.payment.enums.GameTxnType;
import com.ebingo.backend.payment.mappers.GameTransactionMapper;
import com.ebingo.backend.payment.mappers.WalletMapper;
import com.ebingo.backend.payment.repository.GameTransactionRepository;
import com.ebingo.backend.system.exceptions.ResourceNotFoundException;
import com.ebingo.backend.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameTransactionServiceImpl implements GameTransactionService {

    private final GameTransactionRepository gameTransactionRepository;
    private final UserProfileService userProfileService;
    private final RoomRepository roomRepository;
    private final WalletService walletService;

//    @Override
//    public Mono<GameTransactionDto> createGameTransaction(Long userProfileId, BigDecimal amount, GameTxnType gameTxnType, Long gameId) {
//        GameTransaction gameTransaction = new GameTransaction();
//        gameTransaction.setGameId(gameId);
//        gameTransaction.setPlayerId(userProfileId);
//        gameTransaction.setTxnAmount(amount);
//        gameTransaction.setTxnType(gameTxnType);
//        gameTransaction.setTxnStatus(GameTxnStatus.SUCCESS);
//        return gameTransactionRepository.save(gameTransaction)
//                .map(GameTransactionMapper::toDto)
//                .doOnSuccess(dto -> log.info("Game transaction created: {}", dto))
//                .doOnError(err -> log.error("Failed to create game transaction", err));
//
//        // TODO: update wallet balance accordingly
//    }


    @Override
    public Mono<GameTransactionDto> createGameTransaction(
            Long userProfileId, BigDecimal amount, GameTxnType gameTxnType, Long gameId) {

        GameTransaction gameTransaction = new GameTransaction();
        gameTransaction.setGameId(gameId);
        gameTransaction.setPlayerId(userProfileId);
        gameTransaction.setTxnAmount(amount);
        gameTransaction.setTxnType(gameTxnType);
        gameTransaction.setTxnStatus(GameTxnStatus.SUCCESS);

        return gameTransactionRepository.save(gameTransaction)
                .map(GameTransactionMapper::toDto)
                .doOnSuccess(dto -> log.info("Game transaction created: {}", dto))
                .doOnError(err -> log.error("Failed to create game transaction", err))
                .flatMap(dto -> handleWalletOperation(userProfileId, amount, gameTxnType)
                        .thenReturn(dto));
    }

    /**
     * Handles wallet credit or debit based on transaction type.
     */
    private Mono<Void> handleWalletOperation(Long userProfileId, BigDecimal amount, GameTxnType txnType) {
        return walletService.getWalletByUserProfileId(userProfileId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Wallet not found for userProfileId: " + userProfileId)))
                .flatMap(walletDto -> {
                    if (GameTxnType.PRIZE_PAYOUT.equals(txnType) || GameTxnType.REFUND.equals(txnType)) {
                        return walletService.credit(WalletMapper.toEntity(walletDto), amount, txnType)
                                .doOnSuccess(w -> log.info(" Credited wallet {} with {}", walletDto.getId(), amount))
                                .then();
                    } else if (GameTxnType.GAME_FEE.equals(txnType)) {
                        return walletService.debit(WalletMapper.toEntity(walletDto), amount, txnType)
                                .doOnSuccess(w -> log.info("Debited wallet {} with {}", walletDto.getId(), amount))
                                .then();
                    }
                    return Mono.empty();
                });
    }


    @Override
    public Mono<GameTransactionDto> getTransactionByUserIdAndGameId(Long id, Long gameId, GameTxnType gameTxnType) {
        return gameTransactionRepository
                .findByPlayerIdAndGameIdAndTxnType(id, gameId, gameTxnType)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        String.format("Game transaction not found for userId=%d, gameId=%d, type=%s",
                                id, gameId, gameTxnType)
                )))
                .map(GameTransactionMapper::toDto);
    }

    @Override
    public Flux<GameTransactionDto> getPaginatedGameTransactions(Long telegramId, Integer page, Integer size, String sortBy) {
        int pageNumber = (page != null && page >= 1) ? page : 1;
        int pageSize = (size != null && size > 0 && size <= 100) ? size : 10;
        long offset = (long) (pageNumber - 1) * pageSize;

        return userProfileService.getUserProfileByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found")))
                .flatMapMany(up -> {
                    String sortKey = (sortBy != null) ? sortBy.toLowerCase() : "id";
                    return switch (sortKey) {
                        case "txnamount" -> gameTransactionRepository
                                .findByPlayerIdOrderByTxnAmountDesc(up.getId(), pageSize, offset);
                        case "createdat" -> gameTransactionRepository
                                .findByPlayerIdOrderByCreatedAtDesc(up.getId(), pageSize, offset);
                        default -> gameTransactionRepository
                                .findByPlayerIdOrderByIdDesc(up.getId(), pageSize, offset);
                    };
                })
                .map(GameTransactionMapper::toDto)
                .doOnSubscribe(s -> log.info("Fetching game transactions - Page: {}, Size: {}, SortBy: {}", page, size, sortBy))
                .doOnComplete(() -> log.info("Completed fetching game transactions for user: {}", telegramId))
                .doOnError(e -> log.error("Failed to fetch game transactions: {}", e.getMessage(), e));
    }

    @Override
    public Mono<GameTransactionDto> getTransactionById(Long txnId, Long telegramId) {
        return userProfileService.getUserProfileByTelegramId(telegramId)
                .flatMap(up ->
                        gameTransactionRepository.findByIdAndPlayerId(txnId, up.getId())
                                .switchIfEmpty(Mono.error(
                                        new RuntimeException("Game transaction not found with id: " + txnId + " for user: " + up.getId())
                                ))
                                .map(GameTransactionMapper::toDto)
                ).doOnSubscribe(s -> log.info("Fetching game transaction by id: {}", txnId))
                .doOnSuccess(s -> log.info("Game transaction fetched by id: {}", txnId))
                .doOnError(e -> log.error("Failed to fetch game transaction by id: {}", txnId, e));
    }

    @Override
    public Mono<GameTransactionDto> createGameTransactionForPrizePayout(GameState state, Long dbUserId, GameTxnType gameTxnType, Long gameId) {
//        return roomRepository.findById(state.getRoomId())
//                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Room not found with id: " + state.getRoomId())))
//                .flatMap(room -> {
//                    BigDecimal entryFee = room.getEntryFee();
//                    BigDecimal totalPayout = BigDecimal.valueOf(state.getJoinedPlayers().size()).multiply(entryFee);
//                    BigDecimal payout = totalPayout.multiply(BigDecimal.valueOf(0.70));
//                    return createGameTransaction(dbUserId, payout, gameTxnType, gameId);
//                });

        BigDecimal payout = BigDecimal
                .valueOf(state.getAllSelectedCardsIds().size())
                .multiply(BigDecimal.valueOf(state.getEntryFee()))
                .multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(state.getCommissionRate())));
        return createGameTransaction(dbUserId, payout, gameTxnType, gameId);
    }

}
