package com.ebingo.backend.payment.service;

import com.ebingo.backend.game.state.GameState;
import com.ebingo.backend.payment.dto.GameTransactionDto;
import com.ebingo.backend.payment.enums.GameTxnType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface GameTransactionService {
    Mono<GameTransactionDto> createGameTransaction(Long userProfileId, BigDecimal amount, GameTxnType gameTxnType, Long gameId);

    Mono<GameTransactionDto> getTransactionByUserIdAndGameId(Long id, Long gameId, GameTxnType gameTxnType);

    Flux<GameTransactionDto> getPaginatedGameTransactions(String userSupabaseId, Integer page, Integer size, String sortBy);

    Mono<GameTransactionDto> getTransactionById(Long txnId, String userSupabaseId);

    Mono<GameTransactionDto> createGameTransactionForPrizePayout(GameState state, Long dbUserId, GameTxnType gameTxnType, Long gameId);
}
