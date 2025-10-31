package com.ebingo.backend.game.service;

import com.ebingo.backend.game.dto.BingoClaimDto;
import com.ebingo.backend.game.dto.CardInfo;
import com.ebingo.backend.game.dto.GameEndResponse;
import com.ebingo.backend.game.entity.Game;
import com.ebingo.backend.game.entity.Room;
import com.ebingo.backend.game.enums.BingoColumn;
import com.ebingo.backend.game.enums.GamePattern;
import com.ebingo.backend.game.enums.GameStatus;
import com.ebingo.backend.game.mappers.GameEndResponseMapper;
import com.ebingo.backend.game.mappers.GameMapper;
import com.ebingo.backend.game.repository.GameRepository;
import com.ebingo.backend.game.repository.RoomRepository;
import com.ebingo.backend.game.service.state.GameStateService;
import com.ebingo.backend.game.service.state.PlayerCleanupService;
import com.ebingo.backend.game.service.state.PlayerStateService;
import com.ebingo.backend.game.state.GameState;
import com.ebingo.backend.payment.dto.GameTransactionDto;
import com.ebingo.backend.payment.enums.GameTxnType;
import com.ebingo.backend.payment.service.GameTransactionService;
import com.ebingo.backend.payment.service.PaymentService;
import com.ebingo.backend.system.exceptions.PaymentFailedException;
import com.ebingo.backend.system.redis.RedisKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
//@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final Map<Long, GameState> activeGames = new ConcurrentHashMap<>();
    private final RedisPublisher publisher;
    private final CardPoolService cardPoolService;
    private final BingoPatternVerifier patternVerifier;
    private final PlayerStateService playerStateService;
    private final GameStateService gameStateService;
    private final PaymentService paymentService;
    private final ReactiveSetOperations<String, String> setOps;
    private final PlayerCleanupService playerCleanupService;
    private final ReactiveStringRedisTemplate reactiveRedisTemplate;
    private final ReactiveRedisMessageListenerContainer redisListenerContainer;
    private final BingoClaimService bingoClaimService;
    private final ObjectMapper objectMapper;
    private final GameTransactionService gameTransactionService;
    private final GameRepository gameRepository;
    private final RoomRepository roomRepository;
    private final CardSelectionService cardSelectionService;


    // Stop signal sinks per room
    private final Map<Long, MonoSink<Void>> stopLoopSinks = new ConcurrentHashMap<>();
    private final Set<Long> subscribedRooms = ConcurrentHashMap.newKeySet();


    @Value("${game.draw.intervalInSeconds:5}")
    private Integer drawInterval; // seconds

    @Value("${game.countdown.initialInSeconds:30}")
    private Integer initialCountdownSeconds;

    public GameService(RedisPublisher publisher, CardPoolService cardPoolService, BingoPatternVerifier patternVerifier, PlayerStateService playerStateService, GameStateService gameStateService, PaymentService paymentService, ReactiveSetOperations<String, String> setOps, PlayerCleanupService playerCleanupService, ReactiveStringRedisTemplate reactiveRedisTemplate1, ReactiveRedisMessageListenerContainer redisListenerContainer, BingoClaimService bingoClaimService, ObjectMapper objectMapper, GameTransactionService gameTransactionService, GameRepository gameRepository, RoomRepository roomRepository, CardSelectionService cardSelectionService) {
        this.publisher = publisher;
        this.cardPoolService = cardPoolService;
        this.patternVerifier = patternVerifier;
        this.playerStateService = playerStateService;
        this.gameStateService = gameStateService;
        this.paymentService = paymentService;
        this.setOps = setOps;
        this.playerCleanupService = playerCleanupService;
        this.reactiveRedisTemplate = reactiveRedisTemplate1;
        this.redisListenerContainer = redisListenerContainer;
        this.bingoClaimService = bingoClaimService;
        this.objectMapper = objectMapper;
        this.gameTransactionService = gameTransactionService;
        this.gameRepository = gameRepository;
        this.roomRepository = roomRepository;
        this.cardSelectionService = cardSelectionService;
    }

    /**
     * Player joins game and optionally selects a card
     */
//    public Mono<Void> playerJoin(Long roomId, Long gameId, String userId, Integer capacity, BigDecimal entryFee, List<String> selectedCardIds) {
//
//        log.info("===================USER SELECTED CARDS===================>>>>>: {}", selectedCardIds);
//
//        String playersKey = RedisKeys.gamePlayersKey(gameId);
//        AtomicBoolean paymentCompleted = new AtomicBoolean(false);
//
//        log.info("User {} is joining game {}", userId, gameId);
//
//        Mono<String> claimedCardsMono = cardSelectionService.claimCard(roomId, gameId, userId, selectedCardIds, 2);
//
//        return setOps.add(playersKey, userId) // SADD → 1=new user, 0=already joined
//                .flatMap(added -> {
//                    if (added == 0L) {
//                        // Already joined → just send existing state
//                        log.info("User {} is already joined in game {}", userId, gameId);
//                        return afterSuccessfulJoin(roomId, gameId, userId, capacity);
//                    }
//
//                    // New join → attempt payment
//                    return paymentService.processPayment((Long.parseLong(userId)), entryFee, gameId)
//                            .flatMap(paymentSuccess -> {
//                                if (!paymentSuccess) {
//                                    // Payment failed → rollback membership
//                                    return setOps.remove(playersKey, userId)
//                                            .then(publisher.publishUserEvent(userId,
//                                                    Map.of(
//                                                            "type", "error",
//                                                            "payload", Map.of(
//                                                                    "eventType", "game.playerJoinRequest",
//                                                                    "errorType", "paymentFailed",
//                                                                    "message", "Payment failed for user " + userId,
//                                                                    "amount", entryFee
//                                                            )
//                                                    )))
//                                            .then(Mono.error(new PaymentFailedException(Long.parseLong(userId), entryFee)));
//                                }
//
//                                paymentCompleted.set(true);
//                                log.info("Payment complete for user {} in game {}", userId, gameId);
//
//                                // After payment success → complete join
//                                return afterSuccessfulJoin(roomId, gameId, userId, capacity);
//                            })
//                            .onErrorResume(error -> {
//                                // Rollback membership on unexpected errors after payment
//                                log.error("Join failed for user {}: {}", userId, error.getMessage(), error);
//                                if (paymentCompleted.get()) {
//                                    return paymentService.processRefund(Long.parseLong(userId), gameId)
//                                            .onErrorResume(refundErr -> {
//                                                log.error("Refund failed for user {}: {}", userId, refundErr.getMessage(), refundErr);
//                                                return Mono.empty(); // ignore refund error
//                                            })
//                                            .then(setOps.remove(playersKey, userId))
//                                            .then();
//
//                                }
//                                return setOps.remove(playersKey, userId).then();
//                            });
//                });
//    }


//    =========================================================
    public Mono<Void> playerJoin(Long roomId, Long gameId, String userId, Integer capacity,
                                 BigDecimal entryFee, List<String> selectedCardIds) {

        log.info("===== USER {} SELECTED CARDS FOR GAME {} ===== {}", userId, gameId, selectedCardIds);

        String playersKey = RedisKeys.gamePlayersKey(gameId);
        AtomicBoolean paymentCompleted = new AtomicBoolean(false);

        return setOps.add(playersKey, userId) // SADD
                .flatMap(added -> {
                    if (added == 0L) {
                        log.info("User {} already joined game {}", userId, gameId);
                        return afterSuccessfulJoin(roomId, gameId, userId, capacity, selectedCardIds);
                    }

                    // 1️⃣ Claim all cards in parallel
                    return Flux.fromIterable(selectedCardIds)
                            .flatMap(cardId ->
                                    cardSelectionService.claimCard(roomId, gameId, userId, cardId, 2)
                                            .map(result -> Map.entry(cardId, result))
                            )
                            .collectList()
                            .flatMap(results -> {
                                // Check if any claim failed
                                List<Map.Entry<String, String>> failed = results.stream()
                                        .filter(e -> !"OK".equals(e.getValue()))
                                        .toList();

                                if (!failed.isEmpty()) {
                                    String firstError = failed.get(0).getValue();
                                    log.warn("Card claim failed for user {} in game {}: {}", userId, gameId, firstError);

                                    // Notify user about the error via WebSocket
                                    return publisher.publishUserEvent(userId, Map.of(
                                                    "type", "error",
                                                    "payload", Map.of(
                                                            "eventType", "game.playerJoinRequest",
                                                            "message", firstError,
                                                            "failedCards", failed.stream().map(Map.Entry::getKey).toList()
                                                    )
                                            ))
                                            // Rollback membership since they never joined successfully
                                            .then(setOps.remove(playersKey, userId))
                                            .then();
                                }

                                // 2️⃣ All claims succeeded → Process payment
                                log.info("All cards claimed successfully for user {} in game {}. Proceeding with payment...", userId, gameId);
                                return paymentService.processPayment(Long.parseLong(userId), entryFee, gameId)
                                        .flatMap(paymentSuccess -> {
                                            if (!paymentSuccess) {
                                                log.warn("Payment failed for user {} in game {}", userId, gameId);

                                                // 3️⃣ Payment failed → release all claimed cards & refund
                                                return Flux.fromIterable(selectedCardIds)
                                                        .flatMap(cardId -> cardSelectionService.releaseCard(roomId, gameId, userId, cardId))
                                                        .then(paymentService.processRefund(Long.parseLong(userId), gameId)
                                                                .onErrorResume(err -> {
                                                                    log.error("Refund failed for user {}: {}", userId, err.getMessage(), err);
                                                                    return Mono.empty();
                                                                }))
                                                        .then(setOps.remove(playersKey, userId))
                                                        .then(publisher.publishUserEvent(userId, Map.of(
                                                                "type", "error",
                                                                "payload", Map.of(
                                                                        "eventType", "game.playerJoinRequest",
                                                                        "errorType", "paymentFailed",
                                                                        "message", "Payment failed for user " + userId,
                                                                        "amount", entryFee
                                                                )
                                                        )))
                                                        .then(Mono.error(new PaymentFailedException(Long.parseLong(userId), entryFee)));
                                            }

                                            // ✅ Payment success → complete join
                                            paymentCompleted.set(true);
                                            log.info("Payment successful for user {} in game {}", userId, gameId);
                                            return afterSuccessfulJoin(roomId, gameId, userId, capacity, selectedCardIds);
                                        })
                                        .onErrorResume(error -> {
                                            log.error("Unexpected error during payment for user {}: {}", userId, error.getMessage(), error);

                                            if (paymentCompleted.get()) {
                                                return paymentService.processRefund(Long.parseLong(userId), gameId)
                                                        .onErrorResume(refundErr -> {
                                                            log.error("Refund failed for user {}: {}", userId, refundErr.getMessage(), refundErr);
                                                            return Mono.empty();
                                                        })
                                                        .then(Flux.fromIterable(selectedCardIds)
                                                                .flatMap(cardId -> cardSelectionService.releaseCard(roomId, gameId, userId, cardId))
                                                                .then(setOps.remove(playersKey, userId))
                                                                .then());
                                            }

                                            // If payment not completed → release cards, remove user
                                            return Flux.fromIterable(selectedCardIds)
                                                    .flatMap(cardId -> cardSelectionService.releaseCard(roomId, gameId, userId, cardId))
                                                    .then(setOps.remove(playersKey, userId))
                                                    .then();
                                        });
                            });
                });
    }

//    ======================================================================

    /**
     * Send state to a player who is already joined.
     */
    private Mono<Void> sendExistingPlayerState(Long gameId, String userId) {
        return playerStateService.getPlayerState(gameId, userId)
                .flatMap(playerState ->
                        publisher.publishUserEvent(userId,
                                Map.of(
                                        "type", "playerState",
                                        "payload", Map.of(
                                                "userId", userId,
                                                "state", playerState
                                        )
                                )
                        )
                )
                .then();
    }

    /**
     * Steps to perform after a successful payment and join.
     */

    /**
     * Release the countdown lock
     *
     * @param redisTemplate
     * @param lockKey
     * @return
     */
    public Mono<Boolean> releaseCountdownLock(ReactiveStringRedisTemplate redisTemplate, String lockKey) {
        // Lua script: only delete the key if it exists (simple release)
        String luaScript = """
                if redis.call('EXISTS', KEYS[1]) == 1 then
                    return redis.call('DEL', KEYS[1])
                else
                    return 0
                end
                """;

        RedisScript<Long> script = RedisScript.of(luaScript, Long.class);

        return redisTemplate.execute(script, List.of(lockKey))
                .next()
                .map(result -> result != null && result > 0)
                .doOnNext(released -> {
                    if (released) {
                        log.info("Countdown lock {} released", lockKey);
                    } else {
                        log.info("Countdown lock {} was not held", lockKey);
                    }
                });
    }


    private Mono<Void> afterSuccessfulJoin(Long roomId, Long gameId, String userId, Integer capacity, List<String> selectedCardIds) {
        log.info("afterSuccessfulJoin: user {} joined game {}", userId, gameId);

        return gameStateService.getGameState(roomId)
                .flatMap(state -> {
                    log.debug("Game state retrieved: {}", state);

                    Set<String> joinedPlayers = Optional.ofNullable(state.getJoinedPlayers()).orElse(Set.of());
                    int playersCount = joinedPlayers.size();

                    return broadcastPlayerJoin(roomId, userId, joinedPlayers, playersCount, selectedCardIds)
                            .then(startCountdownIfEligible(state, roomId, gameId, userId, capacity, playersCount));
                });
    }


    private Mono<Void> broadcastPlayerJoin(Long roomId, String userId, Set<String> joinedPlayers, int playersCount, List<String> selectedCardIds) {
        Map<String, Object> payload = Map.of(
                "type", "game.playerJoined",
                "payload", Map.of(
                        "joinedPlayers", joinedPlayers,
                        "playerId", userId,
                        "playersCount", playersCount,
                        "playerSelectedCardIds", selectedCardIds
                )
        );

        return publisher.publishEvent(RedisKeys.roomChannel(roomId), payload)
                .doOnSuccess(id -> log.debug("Broadcasted player join event to room {}", roomId))
                .then();
    }

    private Mono<Void> startCountdownIfEligible(
            GameState state,
            Long roomId,
            Long gameId,
            String userId,
            Integer capacity,
            int playersCount
    ) {
        return getMinPlayersToStart(roomId)
                .flatMap(minPlayersToStart -> {
                    // Check eligibility
                    if (playersCount < minPlayersToStart
                            || state.isStarted()
                            || !GameStatus.READY.equals(state.getStatus())) {
                        return Mono.empty();
                    }

                    log.info("Starting countdown for game {}", gameId);
                    String countdownLockKey = RedisKeys.countdownLockKey(gameId);

                    // Lua script with TTL (EX seconds)
                    RedisScript<Long> acquireLockScript = RedisScript.of("""
                            if redis.call('exists', KEYS[1]) == 0 then
                                redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2])
                                return 1
                            else
                                return 0
                            end
                            """, Long.class);

                    // TTL in seconds — you can adjust this safely (e.g. 60)
                    String lockTTL = "60";

                    return reactiveRedisTemplate.execute(acquireLockScript, List.of(countdownLockKey), "locked", lockTTL)
                            .next()
                            .cast(Long.class)
                            .flatMap(acquired -> {
                                if (acquired != null && acquired == 1) {
                                    log.info("Countdown lock acquired for game {}", gameId);

                                    return startCountdownByGameId(roomId, gameId, userId, capacity, initialCountdownSeconds)
                                            // Release lock after countdown completes
                                            .then(releaseCountdownLock(reactiveRedisTemplate, countdownLockKey))
                                            .doOnError(err -> log.error("Countdown failed for game {}", gameId, err))
                                            .onErrorResume(err ->
                                                    releaseCountdownLock(reactiveRedisTemplate, countdownLockKey)
                                                            .then(Mono.error(err))
                                            );
                                } else {
                                    log.info("Countdown already started for game {}", gameId);
                                    return Mono.empty();
                                }
                            })
                            .onErrorResume(err -> {
                                log.error("Error acquiring countdown lock for game {}", gameId, err);
                                return Mono.empty();
                            });
                })
                .then(); // completes with Mono<Void>
    }


    public Mono<Void> leaveGame(Long roomId, Long gameId, String userId) {
        System.out.println("User " + userId + " is leaving game " + gameId);
        return gameStateService.getGameState(roomId)
                .flatMap(state -> {
                    if (state == null) {
                        return publisher.publishUserEvent(userId,
                                Map.of(
                                        "type", "error",
                                        "payload", Map.of(
                                                "eventType", "game.playerLeaveRequest",
                                                "errorType", "invalidGame",
                                                "message", "State not found for game.",
                                                "userId", userId,
                                                "gameId", gameId,
                                                "roomId", roomId
                                        )
                                )).then();
                    }

                    String playersKey = RedisKeys.gamePlayersKey(state.getGameId());
                    String roomPlayersKey = RedisKeys.roomPlayersKey(roomId);

                    boolean gameStarted = state.isStarted();
                    boolean gameEnded = state.isEnded();

                    Instant gameCountdownEndTime = state.getCountdownEndTime();
                    long timeLeft = 15; // default to 15 seconds

                    if (gameCountdownEndTime != null) {
                        timeLeft = Instant.now().until(gameCountdownEndTime, ChronoUnit.SECONDS);
                    } else {
                        // Handle the case where countdown end time is missing
                        log.warn("Countdown end time is null for game state with game id: {}", state.getGameId());
                        // or some default value
                    }

                    if (gameStarted) {
                        // Already started → personal acknowledgement only
                        log.info("User {} tried to cancel, but game {} already started", userId, gameId);
                        return publisher.publishUserEvent(userId,
                                Map.of(
                                        "type", "game.playerLeft",
                                        "payload", Map.of(
                                                "errorType", "gameStarted",
                                                "message", "Game already started.",
                                                "userId", userId,
                                                "gameId", gameId,
                                                "roomId", roomId
                                        )
                                )).then();
                    }

                    if (gameEnded) {
                        // Game already ended → personal acknowledgement only
                        log.info("User {} tried to cancel, but game {} already ended", userId, gameId);
                        return publisher.publishUserEvent(userId,
                                Map.of(
                                        "type", "game.playerLeft",
                                        "payload", Map.of(
                                                "errorType", "gameEnded",
                                                "message", "Game already ended.",
                                                "userId", userId,
                                                "gameId", gameId,
                                                "roomId", roomId
                                        )
                                )).then();
                    }

                    if (timeLeft <= 10 && timeLeft >= 0) {
                        // Game is almost starting → personal acknowledgement only
                        log.info("User {} tried to cancel, but game {} is almost starting", userId, gameId);
                        return publisher.publishUserEvent(userId,
                                Map.of(
                                        "type", "game.playerLeft",
                                        "payload", Map.of(
                                                "errorType", "gameStarting",
                                                "message", "Game is almost starting.",
                                                "userId", userId,
                                                "gameId", gameId,
                                                "roomId", roomId
                                        )
                                )).then();
                    }

                    // Game not started → attempt SREM
                    return setOps.remove(playersKey, userId)
                            .flatMap(removed -> {
                                // Explicitly check if user was in the game
                                if (removed == 0) {
                                    log.warn("User {} was not part of game {} when attempting cancel", userId, gameId);
                                    return publisher.publishUserEvent(userId,
                                            Map.of(
                                                    "type", "error",
                                                    "payload", Map.of(
                                                            "eventType", "game.playerLeaveRequest",
                                                            "errorType", "notInGame",
                                                            "userId", userId,
                                                            "gameId", gameId,
                                                            "roomId", roomId,
                                                            "message", "You were not part of the game."
                                                    )
                                            )).then();
                                }

                                log.info("User {} successfully removed from game {}", userId, gameId);

                                // Refund payment
                                Mono<Boolean> refund = paymentService.processRefund(Long.parseLong(userId), gameId)
                                        .doOnNext(refunded -> log.info("Refund {} for user {} in game {}",
                                                refunded ? "succeeded" : "failed", userId, gameId));

                                // Broadcast updated players
                                Mono<Long> broadcastPlayers = gameStateService.getGameState(roomId)
                                        .flatMap(updatedState -> {
                                            Set<String> players = updatedState.getJoinedPlayers();
                                            int playersCount = players.size();

                                            return playerCleanupService.removePlayerFromGame(roomId, gameId, userId)
                                                    .flatMap(cardIds -> {
                                                        return publisher.publishEvent(
                                                                RedisKeys.roomChannel(roomId),
                                                                Map.of(
                                                                        "type", "game.playerLeft",
                                                                        "payload", Map.of(
                                                                                "playerId", userId,
                                                                                "gameId", gameId,
//                                                                                "gameState", updatedState,
                                                                                "joinedPlayers", players,
                                                                                "playersCount", playersCount,
                                                                                "releasedCardsIds", cardIds,
                                                                                "roomId", roomId
                                                                        )
                                                                )
                                                        );
                                                    });
                                        });
                                return refund.then(broadcastPlayers).then(setOps.remove(roomPlayersKey, userId)).then();
                            });
                })
                .onErrorResume(error -> {
                    log.error("Error in playerCancel for user {} in room {}: {}", userId, roomId, error.getMessage(), error);
                    // Always send personal error acknowledgement
                    return publisher.publishUserEvent(userId,
                            Map.of(
                                    "type", "error",
                                    "payload", Map.of(
                                            "errorType", "leaveError",
                                            "userId", userId,
                                            "gameId", gameId,
                                            "success", false,
                                            "message", "Unable to cancel: " + error.getMessage()
                                    )
                            )).then();
                });
    }


    /**
     * Start countdown for a game
     */
    public Mono<Void> startCountdownByGameId(Long roomId, Long gameId, String userId, Integer capacity, int countdownSeconds) {
        Instant countdownEndTime = Instant.now().plusSeconds(countdownSeconds);
        Mono<Boolean> updateGameState = gameStateService.getGameState(roomId)
                .flatMap(state -> {
                    // Update countdown end time and status
                    state.setCountdownEndTime(countdownEndTime);
                    state.setStatus(GameStatus.COUNTDOWN);
                    state.setStatusUpdatedAt(Instant.now());
                    return gameStateService.saveGameStateToRedis(state, roomId);
                });

        // Publish countdown start event (only once)
        Mono<Long> countdownEvent = publisher.publishEvent(
                RedisKeys.roomChannel(roomId),
                Map.of(
                        "type", "game.countdown",
                        "payload", Map.of(
                                "roomId", roomId,
                                "gameId", gameId,
                                "seconds", countdownSeconds,
                                "countdownEndTime", countdownEndTime.toString()
                        )
                )
        );

        // Run countdown internally, then conditionally start game

        return getMinPlayersToStart(roomId)
                .flatMap(minPlayersToStart -> {
                    return updateGameState
                            .then(countdownEvent)
                            .thenMany(
                                    Flux.range(0, countdownSeconds)
                                            .delayElements(Duration.ofSeconds(1))
                                            .doOnNext(sec -> log.debug("Countdown {} / {}", sec + 1, countdownSeconds))
                            )
                            .then(
                                    // After countdown, check player count again before starting
                                    Mono.defer(() ->
                                            gameStateService.getAllPlayers(gameId)
                                                    .flatMap(state -> {
                                                        int playersCount = state.size();
                                                        log.info("Countdown finished. Players: {} / min: {}", playersCount, minPlayersToStart);
                                                        if (playersCount >= minPlayersToStart) {
                                                            return startGame(gameId, roomId, userId, capacity);
                                                        } else {
                                                            log.warn("Not enough players after countdown. Game {} will not start.", gameId);

                                                            return gameStateService.getGameState(roomId)
                                                                    .flatMap(gState -> {
                                                                        // Reset game state to READY
                                                                        gState.setStatus(GameStatus.READY);
                                                                        gState.setCountdownEndTime(null);
                                                                        gState.setStatusUpdatedAt(Instant.now());
                                                                        return gameStateService.saveGameStateToRedis(gState, roomId)
                                                                                .then(updateGameToDatabase(gState))
                                                                                .then(publisher.publishEvent(
                                                                                        RedisKeys.roomChannel(roomId),
                                                                                        Map.of(
                                                                                                "type", "game.state",
                                                                                                "payload", Map.of(
                                                                                                        "roomId", roomId,
                                                                                                        "gameId", gameId,
                                                                                                        "status", gState.getStatus(),
                                                                                                        "joinedPlayers", gState.getJoinedPlayers(),
                                                                                                        "playersCount", playersCount
                                                                                                )
                                                                                        )
                                                                                ));
                                                                    }).then();
                                                        }
                                                    })
                                    )
                            );
                }).then();

    }


    /**
     * Start the game
     */
    private Mono<Void> startGame(Long gameId, Long roomId, String userId, Integer capacity) {
        return gameStateService.getGameState(roomId)
                .flatMap(state -> {

                    // Update game state to started and playing
                    state.setStarted(true);
                    state.setEnded(false);
                    state.setStatus(GameStatus.PLAYING);
                    state.setStatusUpdatedAt(Instant.now());

                    // Save the updated state first
                    return gameStateService.saveGameStateToRedis(state, roomId)
                            .then(publisher.publishEvent(
                                    RedisKeys.roomChannel(roomId),
                                    Map.of(
                                            "type", "game.started",
                                            "payload", Map.of(
                                                    "message", "Game has started.",
                                                    "roomId", roomId,
                                                    "gameId", gameId
                                            ) // empty payload
                                    )
                            ))
                            .then(
                                    startNumberDrawingWithLuaLock(reactiveRedisTemplate, state, userId)
                                            .onErrorResume(e -> {
                                                log.error("Number drawing failed", e);
                                                return Mono.empty();
                                            })
                            );
                });
    }

    /**
     * Start number drawing with distributed lock to ensure only one instance handles it
     */

    private Mono<Void> startNumberDrawingWithLuaLock(ReactiveStringRedisTemplate redisTemplate,
                                                     GameState state,
                                                     String userId) {
        String lockKey = RedisKeys.gameDrawingLockKey(state.getGameId());
        String lockValue = UUID.randomUUID().toString(); // unique owner
        int lockTTLSeconds = 250;

        // Lua script: acquire lock with NX + EX
        String acquireScriptStr = """
                if redis.call('set', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then
                    return 1
                else
                    return 0
                end
                """;

        RedisScript<Long> acquireScript = RedisScript.of(acquireScriptStr, Long.class);

        // Lua script: release only if owner matches
        String releaseScriptStr = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """;

        RedisScript<Long> releaseScript = RedisScript.of(releaseScriptStr, Long.class);

        return redisTemplate.execute(acquireScript, List.of(lockKey), lockValue, String.valueOf(lockTTLSeconds))
                .next()
                .flatMap(acquired -> {
                    if (acquired != null && acquired == 1L) {
                        log.info("Instance acquired drawing lock for game {}", state.getGameId());

                        return gameStateService.deleteDrawnNumbers(state.getGameId())
                                .flatMap(deleted -> {
                                    return drawNumbersLoop(state, userId)
                                            .then(redisTemplate.execute(releaseScript, List.of(lockKey), lockValue)
                                                    .next()
                                                    .map(result -> result != null && result > 0)
                                                    .doOnNext(released -> {
                                                        if (released) {
                                                            log.info("Released drawing lock for game {}", state.getGameId());
                                                        } else {
                                                            log.warn("Lock for game {} was not released (owner mismatch or expired)", state.getGameId());
                                                        }
                                                    })
                                                    .onErrorResume(e -> {
                                                        log.warn("Failed to unlock drawing lock for game {}", state.getGameId(), e);
                                                        return Mono.empty();
                                                    })
                                            )
                                            .then();

                                });

                    } else {
                        log.info("Another instance is handling number drawing for game {}", state.getGameId());
                        return Mono.empty();
                    }
                });
    }


    // =======================
    // 1️⃣ Auto-subscribe Redis Pub/Sub stop channel
    // =======================
    private void autoSubscribeStopChannel(Long roomId) {
        if (!subscribedRooms.add(roomId)) return; // already subscribed

        String channel = "bingo:room:" + roomId + ":stop";

        redisListenerContainer.receive(new ChannelTopic(channel))
                .map(ReactiveSubscription.Message::getMessage)
                .subscribe(message -> {
                    MonoSink<Void> sink = stopLoopSinks.get(roomId);
                    if (sink != null) {
                        sink.success();               // stop the draw loop
                        stopLoopSinks.remove(roomId); // cleanup
                    }
                    subscribedRooms.remove(roomId); // allow resubscribe for new game
                });
    }


    private Mono<Void> drawNumbersLoop(GameState state, String userId) {
        final Long roomId = state.getRoomId();
        final int maxDraws = 75;
        final String endLockKey = "game:end-lock:" + roomId; // New lock key for endGame

        log.info(">>>>>>>>>>>>>>>>>>>>>><<<<<<DRAW INTERVAL>>>>>>>>>>>>>>>>>>><<<<>>>>>>>>:: {}", drawInterval);
        // Automatically subscribe to Redis stop channel
        autoSubscribeStopChannel(roomId);

        return Mono.<Void>create(sink -> {
            stopLoopSinks.put(roomId, sink);

            Flux.range(1, maxDraws)
                    .concatMap(i ->
                            Mono.delay(Duration.ofSeconds(drawInterval))
                                    .flatMap(tick -> gameStateService.getGameState(roomId))
                                    .flatMap(latestState -> {
                                        if (latestState.isEnded() || latestState.getStopNumberDrawing()) {
                                            log.info("Game {} ended/stopped at iteration {}", latestState.getGameId(), i);
                                            return Mono.empty();
                                        }

                                        // Compute remaining numbers
                                        List<Integer> remaining = new ArrayList<>();
                                        for (int n = 1; n <= maxDraws; n++) {
                                            if (!latestState.getDrawnNumbers().contains(n)) remaining.add(n);
                                        }

                                        if (remaining.isEmpty()) return Mono.empty();

                                        Collections.shuffle(remaining);
                                        Integer next = remaining.get(0);

                                        log.info("Drawing number {} for game {} remaining: {}", next, latestState.getGameId(), remaining);

                                        return drawSingleNumber(latestState, next);
                                    })
                    )
                    // Stop drawing if Redis sends a stop signal
                    .takeUntilOther(Mono.<Void>create(innerSink -> stopLoopSinks.put(roomId, innerSink)))
                    .doFinally(signal -> stopLoopSinks.remove(roomId))
                    .then(
                            // After drawing finishes, handle potential no-winner ending safely
                            Mono.defer(() ->
                                    gameStateService.getGameState(roomId)
                                            .flatMap(latestState -> {
                                                if (latestState.isEnded() || latestState.getStopNumberDrawing()) {
                                                    log.info("Game {} already ended before no-winner check", latestState.getGameId());
                                                    return Mono.empty();
                                                }

                                                // Compute remaining numbers
                                                List<Integer> remaining = new ArrayList<>();
                                                for (int n = 1; n <= maxDraws; n++) {
                                                    if (!latestState.getDrawnNumbers().contains(n)) remaining.add(n);
                                                }

                                                if (!remaining.isEmpty()) {
                                                    log.info("Game {} still has remaining numbers, skipping no-winner end", latestState.getGameId());
                                                    return Mono.empty();
                                                }

                                                log.info("All numbers drawn for game {}. Waiting {}s for potential claims...", latestState.getGameId(), 3);

                                                return Mono.delay(Duration.ofSeconds(3))
                                                        .then(gameStateService.getGameState(roomId))
                                                        .flatMap(checkState -> {
                                                            if (checkState.isEnded() || checkState.getClaimRequested()) {
                                                                log.info("Claim detected or game ended for {} — skipping no-winner end", checkState.getGameId());
                                                                return Mono.empty();
                                                            }

                                                            // Try to acquire end-lock before marking game ended
                                                            return reactiveRedisTemplate.opsForValue().setIfAbsent(endLockKey, "locked", Duration.ofSeconds(10))
                                                                    .flatMap(acquired -> {
                                                                        if (!Boolean.TRUE.equals(acquired)) {
                                                                            log.info("Another instance is already ending game {}", checkState.getGameId());
                                                                            return Mono.empty();
                                                                        }

                                                                        log.info("No claims received. Ending game {} as no-winner.", checkState.getGameId());

                                                                        GameEndResponse response = GameEndResponse.builder()
                                                                                .gameId(checkState.getGameId())
                                                                                .cardId("")
                                                                                .playerId(0L)
                                                                                .playerName("No Winner")
                                                                                .pattern("")
                                                                                .prizeAmount(BigDecimal.ZERO)
                                                                                .hasWinner(false)
                                                                                .winAt(LocalDateTime.now())
                                                                                .markedNumbers(Set.of())
                                                                                .card(new CardInfo())
                                                                                .build();

                                                                        return gameStateService.getGameState(roomId)
                                                                                .flatMap(gameState -> {
                                                                                    if (!gameState.isEnded() && !gameState.getStopNumberDrawing()) {
                                                                                        return endGame(checkState, userId, response).then();
                                                                                    }
                                                                                    return Mono.empty();
                                                                                })
                                                                                .onErrorResume(err -> {
                                                                                    log.info("Error in endGame: {}", err.getMessage());
                                                                                    return Mono.empty();
                                                                                })
                                                                                .then(reactiveRedisTemplate.delete(endLockKey)) // release lock
                                                                                .onErrorResume(err ->
                                                                                        reactiveRedisTemplate.delete(endLockKey).then(Mono.error(err))
                                                                                );
                                                                    });
                                                        });
                                            })
                            )
                    )
                    .doFinally(signal -> log.info("Number drawing loop for room {} finished with signal {}", roomId, signal))
                    .subscribe();
        });
    }


    /**
     * Draw a single number and update state
     */
    private Mono<Void> drawSingleNumber(GameState state, Integer number) {
        return Mono.defer(() -> {
            if (state.isEnded() || state.getStopNumberDrawing()) {
                log.info("=============================>>> Game {} ended during drawing, stopping", state.getGameId());
                return Mono.empty(); // Stop if game ended
            }

            state.getDrawnNumbers().add(number);
//            log.info("Drawing number {} for game {}: ", number, state.getGameId());
            log.info("=====================================>>>: Drawing number {} for game {} and room {}: drawnNumbers={}", number, state.getGameId(), state.getRoomId(), state.getDrawnNumbers());

            // Save updated state to Redis
            return gameStateService.saveGameStateToRedis(state, state.getRoomId())
                    .then(gameStateService.addOrInitDrawnNumber(state.getGameId(), number))
                    .then(publisher.publishEvent(
                            RedisKeys.roomChannel(state.getRoomId()),
                            Map.of("type", "game.numberDrawn",
                                    "payload", Map.of(
                                            "number", number,
                                            "gameId", state.getGameId(),
                                            "roomId", state.getRoomId()))
                    ))
                    .then();
        });
    }

    /**
     * End game when no winner is found (all numbers drawn)
     */
    private Mono<Void> endGame(GameState state, String userId, GameEndResponse responseObject) {
        state.setEnded(true);
        state.setStatus(GameStatus.COMPLETED);

        Long gameId = state.getGameId();

//        return gameStateService.saveGameStateToRedis(state, state.getRoomId())
        return gameStateService.deleteGameState(state.getRoomId())
                .then(
                        publisher.publishEvent(
                                RedisKeys.roomChannel(state.getRoomId()),
                                Map.of("type", "game.ended", "payload", GameEndResponseMapper.toMap(responseObject))
                        )).then();
    }


    private Mono<Boolean> updateGameToDatabase(GameState latestState) {
        return gameRepository.findById(latestState.getGameId())
                .flatMap(existingGame -> {
                    Game updatedGame = GameMapper.toEntity(latestState, existingGame, objectMapper);

                    return gameRepository.save(updatedGame)
                            .doOnSubscribe(sub -> log.info("Updating game {} to database", updatedGame.getId()))
                            .doOnNext(saved -> log.info("Updated game {} to database", saved.getId()))
                            .thenReturn(true); // Return true after saving
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Game not found for ID: {}", latestState.getGameId());
                    return Mono.just(false);
                }))
                .onErrorResume(e -> {
                    log.error("Failed to update game {}: {}", latestState.getGameId(), e.getMessage(), e);
                    return Mono.just(false);
                });
    }


    public Mono<Void> claimBingo(Long roomId, String userId, Map<String, Object> payload) {
        log.info("BINGO CLAIM PAYLOAD: {}", payload);

        Long gameId = payload.get("gameId") != null ? Long.valueOf(payload.get("gameId").toString()) : null;
        String playerName = payload.get("playerName") != null ? payload.get("playerName").toString() : "";
        String cardId = payload.get("cardId") != null ? payload.get("cardId").toString() : "";
        @SuppressWarnings("unchecked")
        List<Integer> markedList = (List<Integer>) payload.get("markedNumbers");
        String pattern = payload.get("pattern") != null ? payload.get("pattern").toString() : GamePattern.LINE_AND_CORNERS.name();
        Long dbUserId = payload.get("userProfileId") != null ? Long.valueOf(payload.get("userProfileId").toString()) : null;

        if (cardId == null || markedList == null || gameId == null || dbUserId == null || cardId.isBlank()) {
            return sendUserError(userId, cardId, "INVALID_CLAIM", "Invalid claim data");
        }

        Set<Integer> claimedMarkedNumbers = new HashSet<>(markedList);
        String claimLockKey = "game:" + gameId + ":claim-lock";
        String endLockKey = "game:end-lock:" + roomId; // New distributed end lock key

        String luaClaimLock = """
                if redis.call('exists', KEYS[1]) == 0 then
                    redis.call('set', KEYS[1], ARGV[1], 'EX', 10)
                    return 1
                else
                    return 0
                end
                """;

        RedisScript<Long> acquireClaimLockScript = RedisScript.of(luaClaimLock, Long.class);

        return Mono.defer(() ->
                        gameStateService.getGameState(roomId)
                                .flatMap(state -> {
                                    if (state.isEnded()) return Mono.error(new IllegalStateException("GAME_ENDED"));

                                    return reactiveRedisTemplate.execute(acquireClaimLockScript, List.of(claimLockKey), userId)
                                            .next()
                                            .flatMap(acquired -> {
                                                if (acquired == null || acquired != 1)
                                                    return Mono.error(new IllegalStateException("LOCK_BUSY"));
                                                return Mono.just(true);
                                            });
                                })
                )
                .retryWhen(Retry.backoff(10, Duration.ofMillis(100))
                        .maxBackoff(Duration.ofSeconds(2))
                        .filter(e -> e instanceof IllegalStateException && "LOCK_BUSY".equals(e.getMessage()))
                        .onRetryExhaustedThrow((spec, signal) -> new RuntimeException("Failed to acquire claim lock after retries"))
                )
                .onErrorResume(e -> {
                    if (e instanceof IllegalStateException && "GAME_ENDED".equals(e.getMessage())) {
                        return sendUserError(userId, cardId, "GAME_ALREADY_COMPLETED", "Game already completed").hasElement();
                    }
                    return Mono.error(e);
                })
                .flatMap(ignored ->
                                Mono.zip(
                                                gameStateService.getAllPlayers(gameId),
                                                playerStateService.getPlayerCards(gameId, userId)
                                        )
                                        .flatMap(tuple -> {
                                            Set<String> players = tuple.getT1();
                                            Map<String, CardInfo> playerCards = tuple.getT2();

                                            if (!players.contains(userId))
                                                return releaseClaimLock(claimLockKey, userId).then(sendUserError(userId, cardId, "USER_NOT_IN_GAME", "You are not in the game"));

                                            CardInfo cardInfo = playerCards.get(cardId);
                                            if (cardInfo == null)
                                                return releaseClaimLock(claimLockKey, userId).then(sendUserError(userId, cardId, "CARD_NOT_FOUND", "Card not found"));

                                            return playerStateService.getMarkedNumbers(gameId, userId, cardId)
                                                    .flatMap(serverMarkedNumbers -> {
                                                        if (!claimedMarkedNumbers.containsAll(serverMarkedNumbers)) {
                                                            return releaseClaimLock(claimLockKey, userId)
                                                                    .then(sendUserError(userId, cardId, "MARKED_NUMBERS_MISMATCH", "Marked numbers mismatch"))
                                                                    .then(
                                                                            gameStateService.getGameState(roomId)
                                                                                    .flatMap(state -> {
                                                                                        return createClaim(serverMarkedNumbers, state, cardId, cardInfo, dbUserId, pattern, false, "Invalid claim").then(); // convert to Mono<Void> so it chains cleanly

                                                                                    })

                                                                    );
                                                        }

                                                        return Mono.fromCallable(() -> {
                                                                    Map<BingoColumn, List<Integer>> cardNumbers = cardInfo.getNumbers();
                                                                    if (pattern != null && !pattern.isBlank()) {
                                                                        return patternVerifier.verifyPattern(cardNumbers, new HashSet<>(serverMarkedNumbers), pattern);
                                                                    } else {
                                                                        return patternVerifier.verifyLineOrFourCorners(cardNumbers, new HashSet<>(serverMarkedNumbers));
                                                                    }
                                                                })
                                                                .subscribeOn(Schedulers.boundedElastic())
                                                                .flatMap(isWinner -> {
                                                                    if (!Boolean.TRUE.equals(isWinner)) {
                                                                        log.info("===============>>>>HOHO>>>>================>>>>>>>>>>: INVALID CLAIM");
                                                                        return releaseClaimLock(claimLockKey, userId)
                                                                                .then(sendUserError(userId, cardId, "INVALID_BINGO_CLAIM", "Invalid claim"))
                                                                                .then(
                                                                                        gameStateService.getGameState(roomId)
                                                                                                .flatMap(state -> {
                                                                                                    return createClaim(serverMarkedNumbers, state, cardId, cardInfo, dbUserId, pattern, false, "Invalid claim").then(); // convert to Mono<Void> so it chains cleanly
                                                                                                })
                                                                                );
                                                                    }

                                                                    return gameStateService.getGameState(roomId)
                                                                            .flatMap(state -> {
                                                                                if (state.isEnded())
                                                                                    return releaseClaimLock(claimLockKey, userId)
                                                                                            .then(sendUserError(userId, cardId, "GAME_ALREADY_COMPLETED", "Game already completed"))
                                                                                            .then(
                                                                                                    createClaim(serverMarkedNumbers, state, cardId, cardInfo, dbUserId, pattern, false, "Game already completed") // convert to Mono<Void> so it chains cleanly
                                                                                            );

                                                                                // Acquire distributed end-lock before marking game ended
                                                                                return reactiveRedisTemplate.opsForValue().setIfAbsent(endLockKey, "locked", Duration.ofSeconds(10))
                                                                                        .flatMap(acquired -> {
                                                                                            if (!Boolean.TRUE.equals(acquired)) {
                                                                                                log.info("Another instance is already ending game {}.", state.getGameId());
                                                                                                return releaseClaimLock(claimLockKey, userId)
                                                                                                        .then(sendUserError(userId, cardId, "GAME_ENDED_BY_ANOTHER_INSTANCE", "Another instance is already ending game"))
                                                                                                        .then(
                                                                                                                createClaim(serverMarkedNumbers, state, cardId, cardInfo, dbUserId, pattern, false, "Another instance is already ending game") // convert to Mono<Void> so it chains cleanly
                                                                                                        );

                                                                                            }

                                                                                            state.setEnded(true);
                                                                                            state.setStatus(GameStatus.COMPLETED);
                                                                                            state.setClaimRequested(true);
                                                                                            state.setStopNumberDrawing(true);

                                                                                            return gameStateService.saveGameStateToRedis(state, roomId)
                                                                                                    .then(updateGameToDatabase(state))
                                                                                                    .then(cardPoolService.getCard(roomId, cardId))
                                                                                                    .flatMap(card -> {
                                                                                                        GameEndResponse response = GameEndResponse.builder()
                                                                                                                .gameId(state.getGameId())
                                                                                                                .cardId(cardId)
                                                                                                                .playerId(Long.parseLong(userId))
                                                                                                                .playerName(playerName)
                                                                                                                .pattern(pattern)
                                                                                                                .prizeAmount(BigDecimal.ZERO)
                                                                                                                .hasWinner(true)
                                                                                                                .winAt(LocalDateTime.now())
                                                                                                                .markedNumbers(serverMarkedNumbers)
                                                                                                                .card(card)
                                                                                                                .build();

                                                                                                        String channel = "bingo:room:" + roomId + ":stop";
//
                                                                                                        Mono<BingoClaimDto> bingoClaimMono = createBingoClaimDto(serverMarkedNumbers, state, cardId, card, dbUserId, pattern, true, null);

                                                                                                        Mono<GameTransactionDto> gameTransactionMono = gameTransactionService.createGameTransactionForPrizePayout(state, dbUserId, GameTxnType.PRIZE_PAYOUT, gameId);

                                                                                                        return bingoClaimMono.flatMap(bingoClaim ->
                                                                                                                bingoClaimService.createBingoClaim(bingoClaim)
                                                                                                                        .then(reactiveRedisTemplate.convertAndSend(channel, "STOP"))
                                                                                                                        .then(endGame(state, userId, response))
                                                                                                                        .then(gameTransactionMono)
                                                                                                                        .then(reactiveRedisTemplate.delete(endLockKey)) // release end lock
                                                                                                                        .onErrorResume(err ->
                                                                                                                                reactiveRedisTemplate.delete(endLockKey).then(Mono.error(err))
                                                                                                                        ));
                                                                                                    });
                                                                                        });
                                                                            });
                                                                });
                                                    });
                                        })
                )
                .onErrorResume(e -> releaseClaimLock(claimLockKey, userId)
                        .then(sendUserError(userId, cardId, "CLAIM_ERROR", "Failed to process bingo claim"))).then();
    }

    private Mono<Void> createClaim(Set<Integer> serverMarkedNumbers, GameState state, String cardId, CardInfo cardInfo, Long dbUserId, String pattern, Boolean isWinner, String error) {
        return createBingoClaimDto(serverMarkedNumbers, state, cardId, cardInfo, dbUserId, pattern, isWinner, error)
                .flatMap(bingoClaimService::createBingoClaim)
                .doOnSuccess(bingoClaim -> log.info(
                        "Bingo claim for game {} created successfully. Bingo claim id: {}",
                        state.getGameId(), bingoClaim.getId()))
                .doOnError(e -> log.error(
                        "Failed to create bingo claim for game {}.", state.getGameId(), e))
                .then();
    }

    private Mono<BingoClaimDto> createBingoClaimDto(
            Set<Integer> serverMarkedNumbers,
            GameState state,
            String cardId,
            CardInfo card,
            Long dbUserId,
            String pattern,
            Boolean isWinner,
            String error
    ) {
        try {
            if (card != null) {
                // Return directly a Mono.just()
                BingoClaimDto dto = BingoClaimDto.builder()
                        .gameId(state.getGameId())
                        .card(objectMapper.writeValueAsString(card))
                        .playerId(dbUserId)
                        .pattern(GamePattern.valueOf(pattern))
                        .markedNumbers(objectMapper.writeValueAsString(serverMarkedNumbers))
                        .isWinner(isWinner)
                        .error(error)
                        .build();

                return Mono.just(dto);
            } else if (cardId != null) {
                // Use reactive call for card fetch
                return cardPoolService.getCard(state.getRoomId(), cardId)
                        .map(cardInfo -> {
                            try {
                                return BingoClaimDto.builder()
                                        .gameId(state.getGameId())
                                        .card(objectMapper.writeValueAsString(cardInfo))
                                        .playerId(dbUserId)
                                        .pattern(GamePattern.valueOf(pattern))
                                        .markedNumbers(objectMapper.writeValueAsString(serverMarkedNumbers))
                                        .isWinner(true)
                                        .error(error)
                                        .build();
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } else {
                return Mono.error(new IllegalArgumentException("Both card and cardId are null"));
            }
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }


    // Safe lock release helper
    private Mono<Boolean> releaseClaimLock(String claimLockKey, String userId) {
        String luaReleaseLock = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """;

        RedisScript<Long> releaseLockScript = RedisScript.of(luaReleaseLock, Long.class);

        return reactiveRedisTemplate.execute(releaseLockScript, List.of(claimLockKey), userId)
                .next()
                .map(result -> result != null && result == 1)
                .doOnNext(released -> {
                    if (released) {
                        log.debug("Lock released for key {}, user {}", claimLockKey, userId);
                    } else {
                        log.debug("No lock released (not owner or already deleted) for key {}, user {}", claimLockKey, userId);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error releasing lock for key {}, user {}", claimLockKey, userId, e);
                    return Mono.just(false);
                });
    }


    // Helper to send errors
    private Mono<Void> sendUserError(String userId, String cardId, String errorType, String message) {
        return publisher.publishUserEvent(userId,
                Map.of(
                        "type", "error",
                        "payload", Map.of(
                                "message", message,
                                "errorType", errorType,
                                "eventType", "bingo.claim",
                                "cardId", cardId
                        )
                )).then();
    }


    public Mono<GameState> getOrInitializeGame(Long roomId, String userId, Integer capacity) {
        return gameStateService.getOrInitializeGame(roomId, userId, capacity);
    }

    public Mono<Void> markNumber(Long roomId, Long gameId, String userId, Map<String, Object> payload) {
        String cardId = (String) payload.get("cardId");
        Integer number = (Integer) payload.get("number");
        if (cardId == null || cardId.isBlank() || !payload.containsKey("number") || number == null || number < 1 || number > 75) {
            return publisher.publishUserEvent(userId, Map.of(
                    "type", "error",
                    "payload", Map.of(
                            "message", "Invalid markNumber payload",
                            "errorType", "CARD_OR_NUMBER_MISSING_OR_INVALID"
                    )
            )).then();
        }
        return playerStateService.addMarkedNumber(gameId, userId, cardId, number)
                .flatMap(updatedCard -> publisher.publishUserEvent(userId, Map.of(
                        "type", "card.markNumberResponse",
                        "payload", Map.of(
                                "cardId", cardId,
                                "marked", updatedCard
                        )
                )))
                .then();
    }

    public Mono<Void> unmarkNumber(Long roomId, Long gameId, String userId, Map<String, Object> payload) {
        String cardId = (String) payload.get("cardId");
        Integer number = (Integer) payload.get("number");
        if (cardId == null || cardId.isBlank() || !payload.containsKey("number") || number == null || number < 1 || number > 75) {
            return publisher.publishUserEvent(userId, Map.of(
                    "type", "error",
                    "payload", Map.of(
                            "message", "Invalid markNumber payload",
                            "errorType", "CARD_OR_NUMBER_MISSING_OR_INVALID"
                    )
            )).then();
        }
        return playerStateService.removeMarkedNumber(gameId, userId, cardId, number)
                .flatMap(updatedCard -> publisher.publishUserEvent(userId, Map.of(
                        "type", "card.unmarkNumberResponse",
                        "payload", Map.of(
                                "cardId", cardId,
                                "marked", updatedCard
                        )
                )))
                .then();
    }


    public Mono<Integer> getMinPlayersToStart(Long roomId) {
        return roomRepository.findById(roomId)
                .map(Room::getMinPlayers)
                .onErrorMap(e -> new RuntimeException("Error getting room by id: " + roomId, e))
                .doOnSubscribe(s -> log.info("Getting min players to start for room: {}", roomId))
                .doOnSuccess(id -> log.info("Got min players to start for room: {}", roomId));
    }
}