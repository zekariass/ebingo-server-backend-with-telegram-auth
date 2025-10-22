package com.ebingo.backend.game.service.state;

import com.ebingo.backend.game.dto.CardInfo;
import com.ebingo.backend.game.enums.GameStatus;
import com.ebingo.backend.game.mappers.GameMapper;
import com.ebingo.backend.game.repository.GameRepository;
import com.ebingo.backend.game.repository.RoomRepository;
import com.ebingo.backend.game.service.CardPoolService;
import com.ebingo.backend.game.service.RedisPublisher;
import com.ebingo.backend.game.state.GameState;
import com.ebingo.backend.game.state.PlayerState;
import com.ebingo.backend.system.redis.RedisKeys;
import com.ebingo.backend.system.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameStateService {

    private final ReactiveRedisTemplate<String, Object> redis;
    private final ReactiveHashOperations<String, String, Object> hashOps;
    private final ReactiveSetOperations<String, String> setOps;
    private final CardPoolService cardPoolService;
    private final PlayerStateService playerStateService; // Add this dependency
    private final RedissonReactiveClient redissonReactiveClient;
    private final RedisPublisher publisher;
    private final GameRepository repository;
    private final SystemConfigService systemConfigService;
    //    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final ObjectMapper objectMapper;
//    private final RoomStateService roomStateService;

    private static final Duration GAME_STATE_TTL = Duration.ofHours(24);

    // ----------------------------
    // Initialize Game
    // ----------------------------
    public Mono<GameState> getOrInitializeGame(Long roomId, String userId, int capacity) {
        String gameKey = RedisKeys.gameStateKey(roomId);

        return redis.hasKey(gameKey)
                .flatMap(exists -> {
                    if (exists) {
                        return getGameState(roomId)
                                .flatMap(gs -> {
                                    if (gs.isEnded() || gs.getStatus().equals(GameStatus.COMPLETED)) {
                                        return deleteGameState(roomId)
                                                .then(initializeGameWithLock(roomId, userId, capacity));
                                    } else if ((gs.getStatus().equals(GameStatus.PLAYING) || gs.getStatus().equals(GameStatus.COUNTDOWN)) && gs.getStatusUpdatedAt().isBefore(Instant.now().minus(Duration.ofMinutes(4)))) {
                                        return deleteGameState(roomId)
                                                .then(initializeGameWithLock(roomId, userId, capacity));
                                    }

                                    return Mono.just(gs);
                                })
                                .flatMap(gameState -> playerStateService.getPlayerCardIds(gameState.getGameId(), userId)
                                        .flatMap(uc -> {
                                            gameState.setUserSelectedCardsIds(uc);
                                            return Mono.just(gameState);
                                        }));
                    } else {
                        return deleteGameState(roomId)
                                .then(initializeGameWithLock(roomId, userId, capacity));
                    }
                });
    }

    public Mono<GameState> initializeGameWithLock(Long roomId, String userId, int capacity) {
        return initializeGameWithLockWithRetry(roomId, userId, capacity)
                .flatMap(gs ->
                        Mono.zip(
                                cardPoolService.getAllCardIds(roomId),
                                playerStateService.getPlayerCardIds(gs.getGameId(), userId)
                        ).map(tuple -> {
                            gs.setAllCardIds(tuple.getT1());
                            gs.setUserSelectedCardsIds(tuple.getT2());
                            return gs;
                        })
                );


    }

//    private Mono<GameState> initializeGameWithLockWithRetry(Long roomId, String userId, int capacity, int retryCount) {
//        final int MAX_RETRIES = 3;
//
//        if (retryCount >= MAX_RETRIES) {
//            publisher.publishUserEvent(userId,
//                    Map.of(
//                            "type", "error",
//                            "payload", Map.of(
//                                    "message", "Maximum retry to initialize the game."
//                            )
//                    ));
//            return Mono.empty();
//        }
//
//        String lockKey = RedisKeys.gameInitLockKey(roomId);
//        RLockReactive lock = redissonReactiveClient.getLock(lockKey);
//
//        return lock.tryLock(0, 10, TimeUnit.SECONDS) // don't wait, lease 10s
//                .flatMap(isLocked -> {
//                    if (Boolean.TRUE.equals(isLocked)) {
//                        return Mono.usingWhen(
//                                Mono.just(lock),
//                                l -> initializeGame(roomId, capacity)
//                                        .flatMap(this::saveGameStateToDb)
//                                        .flatMap(gsFromDb ->
//                                                cardPoolService.getCurrentPool(roomId)
//                                                        .flatMap(pool ->
//                                                                gsFromDb.setCurrentCardPool(pool)  // this returns Mono<Void>
//                                                                        .then(saveGameStateToRedis(gsFromDb, roomId)) // chain save after setter completes
//                                                                        .thenReturn(gsFromDb) // finally return updated GameState
//                                                        )
//                                        ),
//                                l -> l.unlock().onErrorResume(e -> Mono.empty())
//                        );
//                    } else {
//                        return Mono.delay(Duration.ofSeconds(1)) // backoff
//                                .then(checkAndGetExistingGameState(roomId, capacity))
//                                .switchIfEmpty(Mono.defer(() ->
//                                        initializeGameWithLockWithRetry(roomId, userId, capacity, retryCount + 1)
//                                ));
//                    }
//                });
//    }


    private Mono<GameState> initializeGameWithLockWithRetry(Long roomId, String userId, int capacity) {
        String lockKey = RedisKeys.gameInitLockKey(roomId);
        RLockReactive lock = redissonReactiveClient.getLock(lockKey);
        return Mono.defer(() ->
                        lock.tryLock(0, 30, TimeUnit.SECONDS)
                                .timeout(Duration.ofSeconds(10)) // safeguard if Redis hangs
                                .flatMap(isLocked -> {
                                    if (Boolean.TRUE.equals(isLocked)) {
                                        // Only one instance will enter this block
                                        return Mono.usingWhen(
                                                Mono.just(lock),
                                                l -> initializeGame(roomId, capacity)
                                                        .flatMap(this::saveGameStateToDb)
                                                        .flatMap(gsFromDb ->
                                                                cardPoolService.getCurrentPool(roomId)
                                                                        .flatMap(pool ->
                                                                                gsFromDb.setCurrentCardPool(pool)
                                                                                        .then(saveGameStateToRedis(gsFromDb, roomId))
                                                                                        .thenReturn(gsFromDb)
                                                                        )
                                                        ),
                                                l -> l.unlock().onErrorResume(e -> Mono.empty())
                                        );
                                    } else {
                                        log.debug("Game init lock busy for room {} — will retry shortly", roomId);
                                        return Mono.error(new IllegalStateException("LOCK_BUSY"));
                                    }
                                })
                )
                .retryWhen(
                        Retry.backoff(3, Duration.ofMillis(200))       // retry up to 3 times
                                .maxBackoff(Duration.ofSeconds(2))         // exponential delay up to 2s
                                .filter(e ->
                                        e instanceof IllegalStateException && "LOCK_BUSY".equals(e.getMessage())
                                                || e instanceof TimeoutException
                                )
                                .onRetryExhaustedThrow((spec, signal) ->
                                        new RuntimeException("Failed to acquire initialization lock after retries"))
                )
                .onErrorResume(e -> {
                    // handle final failure gracefully
                    log.warn("Game initialization failed for room {}: {}", roomId, e.toString());
                    publisher.publishUserEvent(userId, Map.of(
                            "type", "error",
                            "payload", Map.of("message", "Failed to initialize game after retries")
                    ));
                    return checkAndGetExistingGameState(roomId, capacity);
                });
    }


    /**
     * Checks Redis for an existing game state.
     * If found, it returns the state; otherwise, it throws an error or returns an empty Mono.
     */
    private Mono<GameState> checkAndGetExistingGameState(Long roomId, int capacity) {
        String gameKey = RedisKeys.gameStateKey(roomId);

        // Check if the key exists in Redis
        return redis.hasKey(gameKey)
                .flatMap(hasKey -> {
                    if (Boolean.TRUE.equals(hasKey)) {
                        return getGameState(roomId); // gameId and capacity can be fetched or passed as needed
                    } else {
                        // The game has not been initialized yet, and we couldn't get the lock.
                        // This is an edge case that might require retrying or an error response.
                        log.error("Game {} is not yet initialized. Please retry.", roomId);
                        return Mono.empty();
                    }
                });
    }


    public Mono<GameState> initializeGame(Long roomId, Integer capacity) {
        // 1. Initialize GameState object
        GameState gameState = new GameState();
        gameState.setRoomId(roomId);
        gameState.setStarted(false);
        gameState.setEnded(false);
        gameState.setStatus(GameStatus.READY);
        gameState.setStopNumberDrawing(false);
        gameState.setClaimRequested(false);
        gameState.setCountdownEndTime(null);
        gameState.setStatusUpdatedAt(Instant.now());


        gameState.setDrawnNumber(new LinkedHashSet<>());
        gameState.setCurrentCardPool(List.of());
        gameState.setJoinedPlayers(Set.of());
        gameState.setUserSelectedCardsIds(Set.of());
        gameState.setAllCardIds(Set.of());
        gameState.setAllSelectedCardsIds(Set.of());

        return Mono.zip(
                cardPoolService.generateAndStoreCurrentPool(roomId, capacity),
                systemConfigService.getSystemConfig("COMMISSION_RATE"),
                roomRepository.findById(roomId)
        ).map(tuple -> {
            var commissionConfig = tuple.getT2();
            log.info("===============_______========________Commission rate: {}", commissionConfig);
            log.info("===============_______========________ROOM: {}", tuple.getT3());
            gameState.setCurrentCardPool(tuple.getT1());
            gameState.setCommissionRate(
                    Double.parseDouble(commissionConfig.getValue())
            );
            gameState.setEntryFee(tuple.getT3().getEntryFee().doubleValue());
            gameState.setCapacity(tuple.getT3().getCapacity());
            return gameState;
        });

    }

    // DB save logic
    private Mono<GameState> saveGameStateToDb(GameState gameState) {

        return Mono.fromCallable(() -> GameMapper.toEntity(gameState, objectMapper))
                .flatMap(gs -> {
                    gs.setStartedAt(LocalDateTime.now());
                    return repository.save(gs);
                })
                .map(savedGame -> {
                    gameState.setGameId(savedGame.getId());
                    return gameState;
                });
    }


    public Mono<Boolean> saveGameStateToRedis(GameState gameState, Long roomId) {
        String gameKey = RedisKeys.gameStateKey(roomId);

        Instant countdownEndTime = gameState.getCountdownEndTime();

        Map<String, Object> gameData = new HashMap<>();
        gameData.put("gameId", String.valueOf(gameState.getGameId()));
        gameData.put("roomId", String.valueOf(gameState.getRoomId()));
        gameData.put("started", String.valueOf(gameState.isStarted()));
        gameData.put("ended", String.valueOf(gameState.isEnded()));
        gameData.put("status", gameState.getStatus().name());
        gameData.put("stopNumberDrawing", String.valueOf(gameState.getStopNumberDrawing()));
        gameData.put("claimRequested", gameState.getClaimRequested());
        gameData.put("entryFee", String.valueOf(gameState.getEntryFee()));
        gameData.put("commissionRate", String.valueOf(gameState.getCommissionRate()));
        gameData.put("capacity", String.valueOf(gameState.getCapacity()));
        gameData.put("statusUpdatedAt", gameState.getStatusUpdatedAt().toString());

        // Add countdownEndTime only if present
        if (countdownEndTime != null) {
            gameData.put("countdownEndTime", countdownEndTime.toString());
        }

        return hashOps.putAll(gameKey, gameData)
                .then(redis.expire(gameKey, GAME_STATE_TTL))
                .thenReturn(true)
                .onErrorResume(e -> {
                    log.error("Failed to save game state for game {}: {}", gameState.getGameId(), e.getMessage(), e);
                    return Mono.just(false);
                });
    }

    // ----------------------------
    // Add Drawn Number (delta)
    // ----------------------------


    public Mono<LinkedHashSet<Integer>> addOrInitDrawnNumber(Long gameId, int number) {
        String drawnKey = RedisKeys.gameDrawnNumbersKey(gameId);

        return redis.hasKey(drawnKey)
                .flatMap(exists -> {
                    Mono<Boolean> addNumber = setOps.add(drawnKey, String.valueOf(number))
                            .thenReturn(true)
                            .onErrorResume(e -> {
                                log.error("Failed to add drawn number {} for game {}: {}", number, gameId, e.getMessage(), e);
                                return Mono.just(false);
                            });

                    if (!exists) {
                        // First time: add + set TTL
                        return addNumber.then(redis.expire(drawnKey, GAME_STATE_TTL))
                                .thenMany(setOps.members(drawnKey))
                                .map(obj -> Integer.valueOf((String) obj))
                                .collect(Collectors.toCollection(LinkedHashSet::new));
                    } else {
                        // Just add
                        return addNumber
                                .thenMany(setOps.members(drawnKey))
                                .map(obj -> Integer.valueOf((String) obj))
                                .collect(Collectors.toCollection(LinkedHashSet::new));
                    }
                });

    }

    public Mono<LinkedHashSet<Integer>> getDrawnNumbers(Long gameId) {
        String drawnKey = RedisKeys.gameDrawnNumbersKey(gameId);
        return setOps.members(drawnKey)
                .map(obj -> Integer.valueOf((String) obj))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .onErrorResume(e -> Mono.just(new LinkedHashSet<>()));
    }

    // ----------------------------
    // PlayerState Management - DELEGATED to PlayerStateService
    // ----------------------------
    public Mono<Boolean> savePlayerState(Long gameId, PlayerState playerState) {
        // Delegate to PlayerStateService for actual storage
        return playerStateService.savePlayerState(gameId, playerState)
                .then(setOps.add(RedisKeys.gamePlayersKey(gameId), playerState.getUserProfileId()))
                .thenReturn(true)
                .onErrorResume(e -> {
                    log.error("Failed to save player {} in game {}: {}", playerState.getUserProfileId(), gameId, e.getMessage(), e);
                    return Mono.just(false);
                });
    }

    public Mono<PlayerState> getPlayerState(Long gameId, String userId) {
        // Delegate to PlayerStateService
        return playerStateService.getPlayerState(gameId, userId);
    }


    // ----------------------------
    // Disqualified Players
    // ----------------------------
    public Mono<Set<Long>> disqualifyPlayer(Long gameId, Long userId) {
        String disqualifiedKey = RedisKeys.gameDisqualifiedKey(gameId);

        return redis.hasKey(disqualifiedKey)
                .flatMap(exists -> {
                    Mono<Long> addUser = setOps.add(disqualifiedKey, userId.toString())
                            .onErrorResume(e -> {
                                log.error("Failed to add disqualified player {} for game {}: {}", userId, gameId, e.getMessage(), e);
                                return Mono.just(0L);
                            });

                    if (!exists) {
                        // First time: add + set TTL
                        return addUser.then(redis.expire(disqualifiedKey, GAME_STATE_TTL))
                                .thenMany(setOps.members(disqualifiedKey))
                                .map(str -> Long.valueOf((String) str))
                                .collect(Collectors.toSet());
                    } else {
                        // Just add
                        return addUser
                                .thenMany(setOps.members(disqualifiedKey))
                                .map(str -> Long.valueOf((String) str))
                                .collect(Collectors.toSet());
                    }

                });
    }


    public Mono<Set<String>> getDisqualifiedPlayers(Long gameId) {
        String disqualifiedKey = RedisKeys.gameDisqualifiedKey(gameId);
        return setOps.members(disqualifiedKey)
                .map(obj -> String.valueOf((String) obj))
                .collect(Collectors.toSet())
                .onErrorResume(e -> Mono.just(Set.of()));
    }

    // ----------------------------
    // Set Winner
    // ----------------------------
    public Mono<Boolean> setGameWinner(Long gameId, Long winnerId) {
        String gameKey = RedisKeys.gameStateKey(gameId);
        return Mono.when(
                        hashOps.put(gameKey, "winnerId", winnerId.toString()),
                        hashOps.put(gameKey, "ended", "true")
                )
                .thenReturn(true)
                .onErrorResume(e -> Mono.just(false));
    }


    public Mono<GameState> getGameState(Long roomId) {
        String gameKey = RedisKeys.gameStateKey(roomId);

        return hashOps.entries(gameKey)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(gameMeta -> {
                    log.info("=======================================>>>> GAME META: {}", gameMeta);
                    if (gameMeta.isEmpty()) {
                        return Mono.empty();
                    }

                    GameState state = new GameState();

                    Object gameIdObj = gameMeta.get("gameId");

                    if (gameIdObj == null || "null".equals(gameIdObj.toString())) {
                        return Mono.empty();
                    }

                    state.setGameId(Long.valueOf(gameMeta.get("gameId").toString()));
                    state.setRoomId(Long.valueOf(gameMeta.get("roomId").toString()));
                    state.setStatus(GameStatus.valueOf(gameMeta.get("status").toString()));
                    state.setStarted(Boolean.parseBoolean(gameMeta.get("started").toString()));
                    state.setEnded(Boolean.parseBoolean(gameMeta.get("ended").toString()));
                    state.setStopNumberDrawing(Boolean.parseBoolean(gameMeta.get("stopNumberDrawing").toString()));
                    state.setClaimRequested(Boolean.parseBoolean(gameMeta.get("claimRequested").toString()));
                    state.setEntryFee(gameMeta.get("entryFee") != null ? Double.parseDouble(gameMeta.get("entryFee").toString()) : 0.0);
                    state.setCommissionRate(gameMeta.get("commissionRate") != null ? Double.parseDouble(gameMeta.get("commissionRate").toString()) : 0.0);
                    state.setCapacity(gameMeta.get("capacity") != null ? Integer.parseInt(gameMeta.get("capacity").toString()) : 0);

                    Object statusUpdatedAtRaw = gameMeta.get("statusUpdatedAt");
                    if (statusUpdatedAtRaw != null) {
                        state.setStatusUpdatedAt(Instant.parse(statusUpdatedAtRaw.toString()));
                    } else {
                        log.warn("Missing statusUpdatedAt in gameMeta for game {}", state.getGameId());
                        state.setStatusUpdatedAt(Instant.now());
                        saveGameStateToRedis(state, roomId).subscribe(null, err -> log.error("Failed to save default statusUpdatedAt for game {}: {}", state.getGameId(), err.getMessage()));
                    }

                    Object countdownRaw = gameMeta.get("countdownEndTime");
                    if (countdownRaw != null) {
                        state.setCountdownEndTime(Instant.parse(countdownRaw.toString()));
                    } else {
                        log.warn("Missing countdownEndTime in gameMeta for game {}", state.getGameId());
                        state.setCountdownEndTime(null);
                    }
                    log.info("====================GAME STATE FROM REDIS===========>>> {}", state);

                    // Fetch all reactive parts
                    Mono<LinkedHashSet<Integer>> drawnNumbers = getDrawnNumbers(state.getGameId());
                    Mono<Set<String>> players = getAllPlayers(state.getGameId());
                    Mono<Set<String>> disqualified = getDisqualifiedPlayers(state.getGameId());
                    Mono<List<CardInfo>> currentCardPool = cardPoolService.getCurrentPool(roomId)
                            .defaultIfEmpty(List.of());
                    Mono<Set<String>> allCardIds = cardPoolService.getAllCardIds(roomId)
                            .defaultIfEmpty(Set.of());

                    Mono<Set<String>> allSelectedCardsIds = playerStateService.getAllSelectedCardsIds(state.getGameId());
//                    Mono<Set<String>> userSelectedCardsIds = playerStateService.getPlayerCardIds(state.getGameId(), ""); // Placeholder userId

                    // Zip everything and apply reactive setters
                    return Mono.zip(drawnNumbers, players, disqualified, currentCardPool, allCardIds, allSelectedCardsIds)
                            .flatMap(tuple ->
                                            state.setCurrentCardPool(tuple.getT4())
//                                            .then(state.setNextCardPool(tuple.getT5()))
                                                    .then(Mono.fromCallable(() -> {


                                                        if (state.getDrawnNumbers().isEmpty()) {
                                                            state.setDrawnNumber(tuple.getT1());
                                                        }

                                                        if (state.getJoinedPlayers().isEmpty()) {
                                                            state.setJoinedPlayers(tuple.getT2());
                                                        }

                                                        if (state.getDisqualifiedUsers().isEmpty()) {
                                                            state.setDisqualifiedPlayers(tuple.getT3());
                                                        }

//                                                        log.info("================ALL CARD SIZE==============>>>>>>: {}", tuple.getT5().size());

                                                        if (state.getAllCardIds().isEmpty()) {
                                                            state.setAllCardIds(tuple.getT5());
                                                        }

                                                        state.setCurrentCardPool(tuple.getT4());

                                                        state.setAllSelectedCardsIds(tuple.getT6());


//                                                        state.setUserSelectedCardsIds(tuple.getT7());
                                                        return state;
                                                    }))
                            );
                })
                .onErrorResume(e -> {
                    log.error("Failed to get game state for room {}: {}", roomId, e.getMessage(), e);
                    return Mono.empty();
                });
    }


    // ----------------------------
    // Delete GameState
    // ----------------------------
    public Mono<Boolean> deleteGameState(Long roomId) {
        return getGameState(roomId)
                .flatMap(state -> {
                    Long gameId = state.getGameId();
                    return redis.delete(
                                    RedisKeys.gameStateKey(roomId),
                                    RedisKeys.gameDrawnNumbersKey(gameId),
                                    RedisKeys.gamePlayersKey(gameId),
                                    RedisKeys.currentCardPoolKey(gameId),
                                    RedisKeys.roomCardsSetKey(roomId)
                            )
                            .map(count -> {

                                log.info("========>>>>>>>========NUMBER OF DELETED KEYS===>>>>>>=============>>>: {}", count);
                                return count > 0;
                            })

                            .onErrorReturn(false);
                })
                .defaultIfEmpty(false) // ensures that if getGameState is empty, Mono emits false
                .doOnSuccess(deleted -> log.info("Deleted game state for roomId={} -> {}", roomId, deleted));
    }


    public Mono<Boolean> deleteDrawnNumbers(Long gameId) {
        return redis.delete(RedisKeys.gameDrawnNumbersKey(gameId))
                .map(count -> count > 0)
                .doOnError(e -> log.error("Failed to delete game state for gameId={}", gameId, e))
                .onErrorReturn(false)
                .doOnSuccess(deleted -> log.info("Deleted game state for gameId={} -> {}", gameId, deleted));
    }


    // ----------------------------
    // Player Management
    // ----------------------------
//    public Mono<Set<Long>> addPlayerToGame(Long gameId, Long userId) {
//        String playersKey = RedisKeys.gamePlayersKey(gameId);
//
//        return redis.hasKey(playersKey)
//                .flatMap(exists -> {
//                    Mono<Long> addUser = setOps.add(playersKey, userId.toString())
//                            .onErrorResume(e -> {
//                                log.error("Failed to add player {} for game {}: {}", userId, gameId, e.getMessage(), e);
//                                return Mono.just(0L);
//                            });
//                    if (!exists) {
//                        // First time: add + set TTL
//                        return addUser.then(redis.expire(playersKey, GAME_STATE_TTL))
//                                .thenMany(setOps.members(playersKey))
//                                .map(str -> Long.valueOf((String) str))
//                                .collect(Collectors.toSet());
//                    } else {
//                        // Just add
//                        return addUser
//                                .thenMany(setOps.members(playersKey))
//                                .map(str -> Long.valueOf((String) str))
//                                .collect(Collectors.toSet());
//                    }
//                });
//    }

    public Mono<Set<Long>> addPlayerToGame(Long gameId, Long userId) {
        String playersKey = RedisKeys.gamePlayersKey(gameId);

        return redis.hasKey(playersKey)
                .flatMap(exists -> {
                    Mono<Long> addUser = setOps.add(playersKey, userId.toString())
                            .onErrorResume(e -> {
                                log.error("Failed to add player {} for game {}: {}", userId, gameId, e.getMessage(), e);
                                return Mono.just(0L);
                            });

                    Mono<Set<Long>> resultMono = addUser
                            .then(Mono.defer(() -> setOps.members(playersKey) // fetch all members
                                    .map(Long::valueOf)
                                    .collect(Collectors.toSet())
                            ));

                    if (!exists) {
                        return resultMono
                                .flatMap(set -> redis.expire(playersKey, GAME_STATE_TTL).thenReturn(set));
                    } else {
                        return resultMono;
                    }
                });
    }


    public Mono<Set<String>> getAllPlayers(Long gameId) {
        String playersKey = RedisKeys.gamePlayersKey(gameId);
        return setOps.members(playersKey)
                .map(String::valueOf)  // safer conversion
                .collect(Collectors.toSet())
                .onErrorResume(e -> {
                    log.warn("Failed to fetch players for game {}: {}", gameId, e.getMessage());
                    return Mono.just(Set.of());
                });
    }


    public Mono<Boolean> removePlayerFromGame(Long gameId, Long userId) {
        String playersKey = RedisKeys.gamePlayersKey(gameId);
        return setOps.remove(playersKey, userId.toString())
                .thenReturn(true);
    }


}
