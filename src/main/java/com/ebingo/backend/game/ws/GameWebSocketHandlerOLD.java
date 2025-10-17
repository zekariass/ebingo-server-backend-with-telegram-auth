//package com.ebingo.backend.game.ws;
//
//import com.ebingo.backend.game.dto.WSMessage;
//import com.ebingo.backend.game.service.CardPoolService;
//import com.ebingo.backend.game.service.CardSelectionService;
//import com.ebingo.backend.game.service.GameService;
//import com.ebingo.backend.game.service.RedisPublisher;
//import com.ebingo.backend.system.redis.RedisKeys;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.redis.connection.ReactiveSubscription;
//import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
//import org.springframework.data.redis.listener.ChannelTopic;
//import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.stereotype.Component;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.reactive.socket.CloseStatus;
//import org.springframework.web.reactive.socket.WebSocketHandler;
//import org.springframework.web.reactive.socket.WebSocketMessage;
//import org.springframework.web.reactive.socket.WebSocketSession;
//import org.springframework.web.util.UriComponentsBuilder;
//import reactor.core.Disposable;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//import reactor.core.publisher.Sinks;
//
//import java.math.BigDecimal;
//import java.util.Map;
//import java.util.Optional;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class GameWebSocketHandlerOLD implements WebSocketHandler {
//
//    private final ReactiveRedisMessageListenerContainer listenerContainer;
//    private final ReactiveStringRedisTemplate redis;
//    private final ObjectMapper mapper;
//    private final CardPoolService cardPoolService;
//    private final CardSelectionService cardSelectionService;
//    private final RedisPublisher publisher;
//    private final GameService gameService;
/// /    private final ReactiveJwtDecoder jwtDecoder;
//
//    private final Map<String, Sinks.Many<WSMessage>> sessionSinks = new ConcurrentHashMap<>();
//
//    @Override
//    public Mono<Void> handle(WebSocketSession session) {
//        log.info("======================>>> New WS connection: {}", session.getId());
//        MultiValueMap<String, String> params = UriComponentsBuilder
//                .fromUri(session.getHandshakeInfo().getUri())
//                .build()
//                .getQueryParams();
//
//        String token = params.getFirst("token");
//        if (token == null || token.isBlank()) {
//            log.warn("WS connection rejected: missing token");
//            return session.close(CloseStatus.BAD_DATA);
//        }
//
//        return jwtDecoder.decode(token)
//                .flatMap(jwt -> {
/// /                    log.info("==============> WS authenticated for user: {}", jwt.getSubject());
//                    return startSession(session, jwt, params);
//                })
//                .onErrorResume(e -> {
//                    log.error("Error: {}", e.getMessage());
//                    return session.close(CloseStatus.NOT_ACCEPTABLE);
//                });
//    }
//
//    private Mono<Void> startSession(WebSocketSession session, Jwt jwt, MultiValueMap<String, String> params) {
//        String sessionId = session.getId();
//        Sinks.Many<WSMessage> sink = Sinks.many().multicast().onBackpressureBuffer();
//        sessionSinks.put(sessionId, sink);
//
//        // Extract user & room info
//        String userId = jwt.getSubject();
//        Long roomId = Optional.ofNullable(params.getFirst("roomId")).map(Long::valueOf).orElse(null);
//        Long gameId = Optional.ofNullable(params.getFirst("gameId")).map(Long::valueOf).orElse(null);
//        BigDecimal entryFee = Optional.ofNullable(params.getFirst("entryFee")).map(BigDecimal::new).orElse(BigDecimal.ZERO);
//        String username = Optional.ofNullable(params.getFirst("username")).orElse("User: " + userId);
//
//        if (userId == null || roomId == null) {
//            return publisher.publishUserEvent(userId,
//                    Map.of("type", "error", "payload", Map.of(
//                            "message", "roomId and userId query params are required",
//                            "errorType", "invalid_params"
//                    ))).then(session.close(CloseStatus.BAD_DATA));
//        }
//
//        // Subscribe to Redis channels
//        Flux<ReactiveSubscription.Message<String, String>> roomFlux = listenerContainer.receive(ChannelTopic.of(RedisKeys.roomChannel(roomId)));
//        Flux<ReactiveSubscription.Message<String, String>> gameFlux = (gameId != null)
//                ? listenerContainer.receive(ChannelTopic.of(RedisKeys.gameChannel(gameId)))
//                : Flux.empty();
//        Flux<ReactiveSubscription.Message<String, String>> userFlux = listenerContainer.receive(ChannelTopic.of(RedisKeys.userChannel(userId)));
//
//        Flux<WSMessage> incomingMessages = Flux.merge(roomFlux, gameFlux, userFlux)
//                .map(ReactiveSubscription.Message::getMessage)
//                .map(msg -> {
//                    try {
//                        return mapper.readValue(msg, WSMessage.class);
//                    } catch (Exception e) {
//                        log.error("Failed to parse Redis message: {}", msg, e);
//                        return new WSMessage("error", Map.of("message", "invalid_message"));
//                    }
//                });
//
//        Disposable redisSubscription = incomingMessages.subscribe(sink::tryEmitNext);
//
//        Flux<WebSocketMessage> outgoing = sink.asFlux()
//                .map(msg -> {
//                    try {
//                        return session.textMessage(mapper.writeValueAsString(msg));
//                    } catch (Exception e) {
//                        log.error("Error serializing WS message", e);
//                        return session.textMessage("{}");
//                    }
//                });
//
//        Mono<Void> incoming = session.receive()
//                .map(WebSocketMessage::getPayloadAsText)
//                .flatMap(text -> {
//                    try {
//                        WSMessage msg = mapper.readValue(text, WSMessage.class);
//                        return handleClientMessage(msg, roomId, gameId, userId, username, sessionId, entryFee);
//                    } catch (Exception e) {
//                        log.error("Invalid WS message: {}", text, e);
//                        return publisher.publishUserEvent(userId,
//                                Map.of("type", "error", "payload", Map.of("message", "invalid_message"))).then();
//                    }
//                })
//                .then();
//
//        // Add user to room
//        Mono<Void> addUser = redis.opsForSet().add(RedisKeys.roomPlayersKey(roomId), userId).then();
//
//        return addUser.then(Mono.when(
//                        session.send(outgoing),
//                        incoming
//                ))
//                .doFinally(sig -> {
//                    redis.opsForSet().remove(RedisKeys.roomPlayersKey(roomId), userId).subscribe();
//                    sessionSinks.remove(sessionId);
//                    redisSubscription.dispose(); // Dispose Redis subscription
//                });
//    }
//
//
//    private Mono<Void> handleClientMessage(
//            WSMessage msg, Long roomId, Long gameId, String userId,
//            String username, String sessionId, BigDecimal entryFee
//    ) {
//        String type = msg.getType();
//        Map<String, Object> payload = msg.getPayload() != null ? msg.getPayload() : Map.of();
//        Integer capacity = (Integer) payload.get("capacity");
//
//        switch (type) {
//            case "room.getGameStateRequest":
//                return gameService.getOrInitializeGame(roomId, userId, capacity)
//                        .flatMap(gs -> publisher.publishUserEvent(userId,
//                                Map.of("type", "room.serverGameState", "payload", Map.of(
//                                        "success", true,
//                                        "error", "",
//                                        "gameState", gs,
//                                        "roomId", roomId
//                                )))).then();
//
//            case "card.cardSelectRequest":
//                String cardId = (payload.get("cardId") != null) ? payload.get("cardId").toString() : null;
//                Long gameIdEvent = (payload.get("gameId") != null) ? Long.valueOf(payload.get("gameId").toString()) : null;
//                return cardSelectionService.claimCard(roomId, gameIdEvent, userId, cardId, 2).then();
//
//            case "card.cardReleaseRequest":
//                String cardId2 = (payload.get("cardId") != null) ? payload.get("cardId").toString() : null;
//                Long gameEventId2 = (payload.get("gameId") != null) ? Long.valueOf(payload.get("gameId").toString()) : null;
//                return cardSelectionService.releaseCard(roomId, gameEventId2, userId, cardId2).then();
//
//            case "game.playerJoinRequest":
//                Long gameEventId3 = (payload.get("gameId") != null) ? Long.valueOf(payload.get("gameId").toString()) : null;
//                BigDecimal fee3 = (payload.get("fee") != null) ? BigDecimal.valueOf(Double.parseDouble(payload.get("fee").toString())) : null;
//                Integer capacity3 = (payload.get("capacity") != null) ? Integer.parseInt(payload.get("capacity").toString()) : 100;
//                return gameService.playerJoin(roomId, gameEventId3, userId, capacity3, fee3).then();
//
//            case "game.playerLeaveRequest":
//                Long gameEventId4 = (payload.get("gameId") != null) ? Long.valueOf(payload.get("gameId").toString()) : null;
//                return gameService.leaveGame(roomId, gameEventId4, userId);
//
//            case "card.markNumberRequest":
//                Long gameEventId5 = (payload.get("gameId") != null) ? Long.valueOf(payload.get("gameId").toString()) : null;
//                return gameService.markNumber(roomId, gameEventId5, userId, payload);
//
//            case "card.unmarkNumberRequest":
//                Long gameEventId6 = (payload.get("gameId") != null) ? Long.valueOf(payload.get("gameId").toString()) : null;
//                return gameService.unmarkNumber(roomId, gameEventId6, userId, payload);
//
//            case "game.bingoClaimRequest":
//                return gameService.claimBingo(roomId, userId, payload);
//            case "ping":
//                return publisher.publishEvent(RedisKeys.roomChannel(roomId),
//                        Map.of("type", "pong",
//                                "payload", Map.of())).then();
//
//            default:
//                return publisher.publishUserEvent(userId,
//                        Map.of("type", "error", "payload",
//                                Map.of("message", "unknown_action",
//                                        "type", type))).then();
//        }
//    }
//
//    public void sendToSession(String sessionId, WSMessage msg) {
//        Optional.ofNullable(sessionSinks.get(sessionId)).ifPresent(s -> s.tryEmitNext(msg));
//    }
//
//    private Mono<Void> publishUserError(String userId, String message, String errorType) {
//        return publisher.publishUserEvent(userId,
//                Map.of("type", "error", "payload",
//                        Map.of("message", message, "errorType", errorType))).then();
//    }
//}
