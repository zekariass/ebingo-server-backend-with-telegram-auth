package com.ebingo.backend.game.service;

import com.ebingo.backend.game.service.state.GameStateService;
import com.ebingo.backend.game.service.state.PlayerStateService;
import com.ebingo.backend.system.redis.RedisKeys;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.client.RedisTimeoutException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardSelectionService {

    private final ReactiveStringRedisTemplate redis;
    private final RedisPublisher publisher;
    private final CardPoolService cardPoolService;
    private final PlayerStateService playerStateService;
    private final GameStateService gameStateService;

//    private static final String CLAIM_SCRIPT =
//            "local ownerKey = KEYS[1] \n" +
//                    "local userOwnedKey = KEYS[2] \n" +
//                    "local cardLockKey = KEYS[3] \n" + // Card level lock key
//                    "local userLockKey = KEYS[4] \n" + // User level lock key
//                    "local cardId = ARGV[1] \n" +
//                    "local userId = ARGV[2] \n" +
//                    "local maxPerUser = tonumber(ARGV[3]) \n" +
//                    "local lockTtl = tonumber(ARGV[4]) \n" +
//                    "\n" +
//                    "-- First, try to acquire card-level lock to prevent concurrent claims on same card\n" +
//                    "local cardLockAcquired = redis.call('set', cardLockKey, userId, 'NX', 'EX', lockTtl)\n" +
//                    "if not cardLockAcquired then\n" +
//                    "    local currentLocker = redis.call('get', cardLockKey)\n" +
//                    "    if currentLocker == userId then\n" +
//                    "        -- User already has the lock (retry scenario), continue\n" +
//                    "    else\n" +
//                    "        return 'CARD_LOCKED'\n" +
//                    "    end\n" +
//                    "end\n" +
//                    "\n" +
//                    "-- Check if card is already taken (permanent state, not just locked)\n" +
//                    "if redis.call('exists', ownerKey) == 1 then\n" +
//                    "    redis.call('del', cardLockKey)  -- Cleanup lock before returning\n" +
//                    "    return 'CARD_TAKEN'\n" +
//                    "end\n" +
//                    "\n" +
//                    "-- Acquire user-level lock to prevent same user from spamming\n" +
//                    "local userLockAcquired = redis.call('set', userLockKey, '1', 'NX', 'EX', lockTtl)\n" +
//                    "if not userLockAcquired then\n" +
//                    "    redis.call('del', cardLockKey)  -- Cleanup card lock\n" +
//                    "    return 'USER_BUSY'\n" +
//                    "end\n" +
//                    "\n" +
//                    "-- Check if user has reached card limit\n" +
//                    "local count = redis.call('scard', userOwnedKey)\n" +
//                    "if tonumber(count) >= maxPerUser then\n" +
//                    "    redis.call('del', cardLockKey)  -- Cleanup locks\n" +
//                    "    redis.call('del', userLockKey)\n" +
//                    "    return 'USER_LIMIT'\n" +
//                    "end\n" +
//                    "\n" +
//                    "-- Claim the card atomically\n" +
//                    "redis.call('set', ownerKey, userId)\n" +
//                    "redis.call('sadd', userOwnedKey, cardId)\n" +
//                    "\n" +
//                    "-- Release all locks\n" +
//                    "redis.call('del', cardLockKey)\n" +
//                    "redis.call('del', userLockKey)\n" +
//                    "return 'OK'";
//
//    private static final RedisScript<String> SCRIPT = RedisScript.of(CLAIM_SCRIPT, String.class);
//
//
//    public Mono<Void> claimCard(Long roomId, Long gameId, String userId, String cardId, int maxCardsPerPlayer) {
//        if (cardId == null) {
//            log.info("==============> Cannot claim card: Card ID is null for user {} in room {}", userId, roomId);
//            return publishCardError(roomId, userId, null, "Card ID cannot be null", "INVALID_CARD_ID");
//        }
//
//        String ownerKey = RedisKeys.cardOwnerKey(gameId, cardId);
//        String userOwnedCardsKey = RedisKeys.userOwnedCardsKey(gameId, userId);
//        String cardLockKey = RedisKeys.cardLockKey(gameId, cardId);
//        String userLockKey = RedisKeys.userLockKey(gameId, userId);
//        int lockTtlSeconds = 10;
//
//        return cardPoolService.cardExistInRoom(cardId, roomId)
//                .flatMap(exist -> {
//                    if (!exist) {
//                        return publishCardError(roomId, userId, cardId, "Card does not exist!", "CARD_DOES_NOT_EXIST");
//                    }
//
//                    return redis.execute(SCRIPT, List.of(ownerKey, userOwnedCardsKey, cardLockKey, userLockKey),
//                                    cardId, userId, String.valueOf(maxCardsPerPlayer), String.valueOf(lockTtlSeconds))
//                            .next()
//                            .flatMap(result -> {
//                                if (result == null) {
//                                    log.error("Card claim returned null for user {} card {}", userId, cardId);
//                                    return publishCardError(roomId, userId, cardId, "Claim operation failed", "UNKNOWN_ERROR");
//                                }
//
//                                return switch (result) {
//                                    case "OK" -> cardPoolService.getCard(roomId, cardId)
//                                            .flatMap(card -> playerStateService.savePlayerCard(gameId, userId, cardId, card)
//                                                    .doOnSuccess(v -> log.info("User {} successfully claimed card {} in room {}", userId, cardId, roomId))
//                                                    .then(playerStateService.addPlayerCardId(gameId, userId, cardId)
//                                                            .doOnSuccess(v -> log.info("Added cardId {} to player {}'s state in game {}", cardId, userId, gameId)))
//                                                    .then(playerStateService.addToAllPlayersSelectedCardsIds(gameId, cardId)
//                                                            .doOnSuccess(v -> log.info("Added cardId {} to all players' selected cards in game {}", cardId, gameId))))
//                                            .then(cardPoolService.addSelectedCard(gameId, cardId))
//                                            .flatMap(selectedCards -> publishSuccess(roomId, cardId, userId, selectedCards))
//                                            .doFinally(s -> log.info("Completed card claim process for user {} card {} in room {}", userId, cardId, roomId));
//
//                                    case "CARD_TAKEN" ->
//                                            publishCardError(roomId, userId, cardId, "Card is already taken", "CARD_TAKEN");
//                                    case "USER_LIMIT" ->
//                                            publishCardError(roomId, userId, cardId, "You have reached the maximum number of cards", "USER_LIMIT");
//                                    case "USER_BUSY" ->
//                                            publishCardError(roomId, userId, cardId, "Please wait before making another selection", "USER_BUSY");
//                                    case "CARD_LOCKED" ->
//                                            publishCardError(roomId, userId, cardId, "Card is being claimed by another user", "CARD_LOCKED");
//                                    default ->
//                                            publishCardError(roomId, userId, cardId, "Unexpected error: " + result, "UNKNOWN_ERROR");
//                                };
//                            })
//                            .onErrorResume(err -> {
//                                log.error("Error claiming card {} for user {} in room {}: {}", cardId, userId, roomId, err.getMessage(), err);
//                                return cleanupLocks(roomId, cardId, userId)
//                                        .then(publishCardError(roomId, userId, cardId, "Internal server error", "UNKNOWN_ERROR"));
//                            });
//                });
//    }


    private static final String CLAIM_SCRIPT =
            "local ownerKey = KEYS[1]\n" +
                    "local userOwnedKey = KEYS[2]\n" +
                    "local cardLockKey = KEYS[3]\n" +
                    "local userLockKey = KEYS[4]\n" +
                    "local cardId = ARGV[1]\n" +
                    "local userId = ARGV[2]\n" +
                    "local maxPerUser = tonumber(ARGV[3])\n" +
                    "local lockTtl = tonumber(ARGV[4])\n" +
                    "\n" +
                    "-- Acquire card-level lock\n" +
                    "local cardLockAcquired = redis.call('set', cardLockKey, userId, 'NX', 'EX', lockTtl)\n" +
                    "if not cardLockAcquired then\n" +
                    "    local currentLocker = redis.call('get', cardLockKey)\n" +
                    "    if currentLocker ~= userId then\n" +
                    "        return 'CARD_LOCKED'\n" +
                    "    end\n" +
                    "end\n" +
                    "\n" +
                    "-- Check if card already owned\n" +
                    "if redis.call('exists', ownerKey) == 1 then\n" +
                    "    redis.call('del', cardLockKey)\n" +
                    "    return 'CARD_TAKEN'\n" +
                    "end\n" +
                    "\n" +
                    "-- Acquire user-level lock\n" +
                    "local userLockAcquired = redis.call('set', userLockKey, '1', 'NX', 'EX', lockTtl)\n" +
                    "if not userLockAcquired then\n" +
                    "    redis.call('del', cardLockKey)\n" +
                    "    return 'USER_BUSY'\n" +
                    "end\n" +
                    "\n" +
                    "-- Enforce per-user card limit\n" +
                    "local count = redis.call('scard', userOwnedKey)\n" +
                    "if tonumber(count) >= maxPerUser then\n" +
                    "    redis.call('del', cardLockKey)\n" +
                    "    redis.call('del', userLockKey)\n" +
                    "    return 'USER_LIMIT'\n" +
                    "end\n" +
                    "\n" +
                    "-- Claim card\n" +
                    "redis.call('set', ownerKey, userId)\n" +
                    "redis.call('sadd', userOwnedKey, cardId)\n" +
                    "\n" +
                    "-- Cleanup locks\n" +
                    "redis.call('del', cardLockKey)\n" +
                    "redis.call('del', userLockKey)\n" +
                    "return 'OK'";

    private static final RedisScript<String> SCRIPT = RedisScript.of(CLAIM_SCRIPT, String.class);

    public Mono<Void> claimCard(Long roomId, Long gameId, String userId, String cardId, int maxCardsPerPlayer) {
        if (cardId == null) {
            log.info("Cannot claim card: cardId is null for user {} in room {}", userId, roomId);
            return publishCardError(roomId, userId, null, "Card ID cannot be null", "INVALID_CARD_ID");
        }

        final String ownerKey = RedisKeys.cardOwnerKey(gameId, cardId);
        final String userOwnedCardsKey = RedisKeys.userOwnedCardsKey(gameId, userId);
        final String cardLockKey = RedisKeys.cardLockKey(gameId, cardId);
        final String userLockKey = RedisKeys.userLockKey(gameId, userId);
        final int lockTtlSeconds = 10; // slightly longer to avoid premature expiry

        return cardPoolService.cardExistInRoom(cardId, roomId)
                .flatMap(exist -> {
                    if (!exist) {
                        return publishCardError(roomId, userId, cardId, "Card does not exist!", "CARD_DOES_NOT_EXIST");
                    }

                    // Execute Lua claim script
                    return redis.execute(
                                    SCRIPT,
                                    List.of(ownerKey, userOwnedCardsKey, cardLockKey, userLockKey),
                                    cardId, userId,
                                    String.valueOf(maxCardsPerPlayer),
                                    String.valueOf(lockTtlSeconds)
                            )
                            .next()
                            .flatMap(result -> {
                                if (result == null) {
                                    log.error("Card claim returned null for user {} card {}", userId, cardId);
                                    return publishCardError(roomId, userId, cardId, "Claim operation failed", "UNKNOWN_ERROR");
                                }

                                switch (result) {
                                    case "OK":
                                        return cardPoolService.getCard(roomId, cardId)
                                                .flatMap(card ->
                                                        playerStateService.savePlayerCard(gameId, userId, cardId, card)
                                                                .doOnSuccess(v -> log.info("User {} saved card {} in game {}", userId, cardId, gameId))
                                                                .then(playerStateService.addPlayerCardId(gameId, userId, cardId)
                                                                        .doOnSuccess(v -> log.info("Added cardId {} to player {} in game {}", cardId, userId, gameId)))
                                                                .then(playerStateService.addToAllPlayersSelectedCardsIds(gameId, cardId)
                                                                        .doOnSuccess(v -> log.info("Added cardId {} to all players selected list", cardId)))
                                                                .then(cardPoolService.addSelectedCard(gameId, cardId))
                                                                .flatMap(selectedCards -> publishSuccess(roomId, cardId, userId, selectedCards))
                                                )
                                                .doFinally(signal -> log.info("Completed claim for user {} card {} room {}", userId, cardId, roomId));

                                    case "CARD_TAKEN":
                                        return publishCardError(roomId, userId, cardId, "Card is already taken", "CARD_TAKEN");

                                    case "USER_LIMIT":
                                        return publishCardError(roomId, userId, cardId, "You have reached the maximum number of cards", "USER_LIMIT");

                                    case "USER_BUSY":
                                        return publishCardError(roomId, userId, cardId, "Please wait before making another selection", "USER_BUSY");

                                    case "CARD_LOCKED":
                                        return publishCardError(roomId, userId, cardId, "Card is being claimed by another user", "CARD_LOCKED");

                                    default:
                                        return publishCardError(roomId, userId, cardId, "Unexpected error: " + result, "UNKNOWN_ERROR");
                                }
                            })
                            .onErrorResume(err -> {
                                log.error("Error claiming card {} for user {} room {}: {}", cardId, userId, roomId, err.getMessage(), err);
                                return cleanupLocks(roomId, cardId, userId)
                                        .then(publishCardError(roomId, userId, cardId, "Internal server error", "UNKNOWN_ERROR"));
                            });
                });
    }


    private static final String RELEASE_SCRIPT =
            "local ownerKey = KEYS[1] \n" +
                    "local userOwnedKey = KEYS[2] \n" +
                    "local cardLockKey = KEYS[3] \n" +
                    "local userLockKey = KEYS[4] \n" +
                    "local cardId = ARGV[1] \n" +
                    "local userId = ARGV[2] \n" +
                    "local lockTtl = tonumber(ARGV[3]) \n" +
                    "\n" +
                    "-- Check if user actually owns this card first (no locks needed for read)\n" +
                    "local currentOwner = redis.call('get', ownerKey)\n" +
                    "if currentOwner == false then\n" +
                    "    return 'CARD_NOT_OWNED'\n" +
                    "end\n" +
                    "\n" +
                    "if currentOwner ~= userId then\n" +
                    "    return 'NOT_OWNER'\n" +
                    "end\n" +
                    "\n" +
                    "-- Acquire locks only if verification passes\n" +
                    "local cardLockAcquired = redis.call('set', cardLockKey, userId, 'NX', 'EX', lockTtl)\n" +
                    "if not cardLockAcquired then\n" +
                    "    return 'CARD_LOCKED'\n" +
                    "end\n" +
                    "\n" +
                    "local userLockAcquired = redis.call('set', userLockKey, '1', 'NX', 'EX', lockTtl)\n" +
                    "if not userLockAcquired then\n" +
                    "    redis.call('del', cardLockKey)\n" +
                    "    return 'USER_BUSY'\n" +
                    "end\n" +
                    "\n" +
                    "-- Perform the release operation\n" +
                    "redis.call('del', ownerKey)\n" +
                    "redis.call('srem', userOwnedKey, cardId)\n" +
                    "\n" +
                    "-- Release locks\n" +
                    "redis.call('del', cardLockKey)\n" +
                    "redis.call('del', userLockKey)\n" +
                    "return 'OK'";

    private static final RedisScript<String> RELEASE_SCRIPT_OBJ = RedisScript.of(RELEASE_SCRIPT, String.class);

    public Mono<Void> releaseCard(Long roomId, Long gameId, String userId, String cardId) {
        String ownerKey = RedisKeys.cardOwnerKey(gameId, cardId);
        String userOwnedKey = RedisKeys.userOwnedCardsKey(gameId, userId);
        String cardLockKey = RedisKeys.cardLockKey(gameId, cardId);
        String userLockKey = RedisKeys.userLockKey(gameId, userId);

        int lockTtlSeconds = 5; // Shorter TTL for release operations

        return redis.execute(RELEASE_SCRIPT_OBJ, List.of(ownerKey, userOwnedKey, cardLockKey, userLockKey),
                        cardId, userId, String.valueOf(lockTtlSeconds))
                .next()
                .flatMap(result -> {
                    if (result == null) {
                        log.error("Card release returned null for user {} card {} in room {}",
                                userId, cardId, roomId);
                        return handleReleaseError(roomId, gameId, userId, cardId, "UNKNOWN_RELEASE_ERROR");
                    }

                    return switch (result) {
                        case "OK" -> cardPoolService.removeSelectedCard(gameId, cardId)
                                .then(playerStateService.removePlayerCard(gameId, userId, cardId))
                                .then(playerStateService.removePlayerCardId(gameId, userId, cardId))
                                .then(playerStateService.removeFromAllPlayersSelectedCardsId(gameId, cardId))
                                .then(cardPoolService.getSelectedCards(gameId)
                                        .flatMap(selectedCards -> {

//                                                    log.info("=======================CARDS====================>>>>, {}", selectedCards);
                                                    return handleSuccessfulRelease(roomId, gameId, userId, cardId, selectedCards);
                                                }
                                        ));


                        case "NOT_OWNER" -> {
                            log.warn("User {} attempted to release unowned card {} in room {}",
                                    userId, cardId, roomId);
                            yield handleReleaseError(roomId, gameId, userId, cardId, "NOT_OWNER");
                        }
                        case "CARD_NOT_OWNED" -> {
                            log.info("Card {} was not owned by anyone in room {}", cardId, roomId);
                            yield handleReleaseError(roomId, gameId, userId, cardId, "CARD_NOT_OWNED");

//                            yield Mono.empty(); // Silent success for idempotent operation
                        }
                        case "USER_BUSY" -> {
                            log.debug("User {} busy during card release for card {} in room {}",
                                    userId, cardId, roomId);
                            yield handleReleaseError(roomId, gameId, userId, cardId, "USER_BUSY");
                        }
                        case "CARD_LOCKED" -> {
                            log.debug("Card {} locked during release by user {} in room {}",
                                    cardId, userId, roomId);
                            yield handleReleaseError(roomId, gameId, userId, cardId, "CARD_LOCKED");
                        }
                        default -> {
                            log.error("Unexpected result during card release: {} for user {} card {} room {}",
                                    result, userId, cardId, roomId);
                            yield handleReleaseError(roomId, gameId, userId, cardId, "UNKNOWN_ERROR");
                        }
                    };
                })
                .onErrorResume(err -> {
                    log.error("Error releasing card {} for user {} in room {}: {}",
                            cardId, userId, roomId, err.getMessage(), err);
                    return handleReleaseError(roomId, gameId, userId, cardId, "UNKNOWN_ERROR");
                })
                .retryWhen(
                        Retry.backoff(3, Duration.ofMillis(100))
                                .filter(this::isRetryableError)
                                .doAfterRetry(rs -> {
                                    log.error("Retry #{} failed for releasing card {} by user {} in room {}",
                                            rs.totalRetriesInARow(), cardId, userId, roomId, rs.failure());
                                })
                )
                .onErrorResume(err -> {
                    log.error("Retries exhausted for releasing card {} by user {} in room {}",
                            cardId, userId, roomId, err);
                    return handleReleaseError(roomId, gameId, userId, cardId, "RETRIES_EXHAUSTED");
                });


//                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
//                                new RuntimeException("Failed to release card after retries")));
    }

    private Mono<Void> handleSuccessfulRelease(Long roomId, Long gameId, String userId, String cardId, Set<String> selectedCards) {
        return Mono.when(
                playerStateService.removePlayerCard(gameId, userId, cardId),
                publishCardReleased(roomId, cardId, userId, selectedCards),
                updateGameStateIfNeeded(roomId, gameId, userId)
        ).then();
    }

    private Mono<Void> handleReleaseError(Long roomId, Long gameId, String userId, String cardId, String errorCode) {
        return publishCardError(roomId, userId, cardId, "Failed to release card: " + errorCode, errorCode);
    }

    private boolean isRetryableError(Throwable error) {
        return error instanceof RedisConnectionException ||
                error instanceof RedisCommandTimeoutException ||
                error instanceof RedisTimeoutException;
    }

    private Mono<Void> updateGameStateIfNeeded(Long roomId, Long gameId, String userId) {
        // Optional: Update game state if card release affects game logic
        return Mono.empty();
    }


    private Mono<Void> publishSuccess(Long roomId, String cardId, String userId, Set<String> selectedCards) {
        return publisher.publishEvent(RedisKeys.roomChannel(roomId),
                Map.of(
                        "type", "game.cardSelected",
                        "payload", Map.of(
                                "cardId", cardId,
                                "playerId", Long.valueOf(userId),
                                "message", "Card claimed successfully",
                                "selectedCards", selectedCards
                        )
                )).then();
    }

    private Mono<Void> publishCardReleased(Long roomId, String cardId, String userId, Set<String> selectedCards) {
        return publisher.publishEvent(RedisKeys.roomChannel(roomId),
                Map.of(
                        "type", "game.cardReleased",
                        "payload", Map.of(
                                "cardId", cardId,
                                "userId", userId,
                                "message", "Card released successfully",
                                "selectedCards", selectedCards
                        )
                )).then();
    }

    private Mono<Void> cleanupLocks(Long roomId, String cardId, String userId) {
        String cardLockKey = RedisKeys.cardLockKey(roomId, cardId);
        String userLockKey = RedisKeys.userLockKey(roomId, userId);

        return Mono.when(
                redis.delete(cardLockKey)
                        .doOnError(err -> log.warn("Failed to cleanup card lock for {}: {}", cardId, err.getMessage()))
                        .onErrorResume(err -> Mono.empty()),

                redis.delete(userLockKey)
                        .doOnError(err -> log.warn("Failed to cleanup user lock for {}: {}", userId, err.getMessage()))
                        .onErrorResume(err -> Mono.empty())
        ).then();
    }


    public Mono<Void> claimCardWithRetry(Long roomId, Long gameId, String userId, String cardId, int maxCardsPerPlayer) {
        return Mono.defer(() -> claimCard(roomId, gameId, userId, cardId, maxCardsPerPlayer))
                .retryWhen(reactor.util.retry.Retry.backoff(3, Duration.ofMillis(200))
                        .filter(err -> {
                            String errorMsg = err.getMessage();
                            return errorMsg != null &&
                                    (errorMsg.contains("CARD_LOCKED") ||
                                            errorMsg.contains("USER_BUSY"));
                        })
                        .doAfterRetry(retrySignal ->
                                log.debug("Retrying card claim for user {} card {} (attempt {})",
                                        userId, cardId, retrySignal.totalRetries() + 1)
                        )
                )
                .onErrorResume(err -> {
                    log.warn("Failed to claim card {} for user {} after retries: {}",
                            cardId, userId, err.getMessage());
                    return publishCardError(roomId, userId, cardId, "Could not claim card, please try again", "RETRY_FAILED");
                });
    }

    private Mono<Void> publishCardError(Long roomId, String userId, String cardId, String message, String errorType) {
        return publisher.publishUserEvent(userId,
                Map.of(
                        "type", "error",
                        "payload", Map.of(
                                "message", message,
                                "roomId", roomId,
                                "cardId", cardId == null ? "" : cardId,
                                "errorType", errorType
                        )
                )).then();
    }

    public Mono<Map<String, Object>> getCardStatus(Long roomId, String cardId) {
        String ownerKey = RedisKeys.cardOwnerKey(roomId, cardId);

        return redis.opsForValue().get(ownerKey)
                .map(ownerId -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("cardId", cardId);
                    result.put("taken", true);
                    result.put("ownerId", ownerId);
                    return result;
                })
                .defaultIfEmpty(createCardStatusResponse(cardId, false, null));
    }

    private Map<String, Object> createCardStatusResponse(String cardId, boolean taken, String ownerId) {
        Map<String, Object> result = new HashMap<>();
        result.put("cardId", cardId);
        result.put("taken", taken);
        result.put("ownerId", ownerId);
        return result;
    }

    public Mono<List<Map<String, Object>>> getMultipleCardStatuses(Long roomId, List<String> cardIds) {
        return Flux.fromIterable(cardIds)
                .flatMap(cardId -> getCardStatus(roomId, cardId))
                .collectList();
    }

    public Mono<Void> releaseCardsForRoom(Long roomId) {
        String roomPrefix = "room:" + roomId + ":";

        return redis.scan()
                .filter(key -> key.startsWith(roomPrefix))
                .collectList()
                .flatMapMany(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.empty();
                    }
                    return redis.delete(keys.toArray(new String[0]));
                })
                .then()
                .doOnSuccess(v -> log.info("Released all cards for room {}", roomId))
                .doOnError(err -> log.error("Failed to release cards for room {}: {}", roomId, err.getMessage(), err));
    }

    public Mono<Boolean> forceReleaseCardLock(Long roomId, String cardId) {
        String cardLockKey = RedisKeys.cardLockKey(roomId, cardId);
        return redis.delete(cardLockKey)
                .map(count -> count > 0)
                .doOnSuccess(released -> {
                    if (released) {
                        log.info("Force released card lock for {} in room {}", cardId, roomId);
                    }
                });
    }

    public Mono<Boolean> forceReleaseUserLock(Long roomId, String userId) {
        String userLockKey = RedisKeys.userLockKey(roomId, userId);
        return redis.delete(userLockKey)
                .map(count -> count > 0)
                .doOnSuccess(released -> {
                    if (released) {
                        log.info("Force released user lock for {} in room {}", userId, roomId);
                    }
                });
    }


}