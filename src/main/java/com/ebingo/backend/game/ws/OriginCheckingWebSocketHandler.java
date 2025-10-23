//package com.ebingo.backend.game.ws;
//
//import org.springframework.web.reactive.socket.CloseStatus;
//import org.springframework.web.reactive.socket.WebSocketHandler;
//import org.springframework.web.reactive.socket.WebSocketSession;
//import reactor.core.publisher.Mono;
//
//import java.util.Set;
//
//public class OriginCheckingWebSocketHandler implements WebSocketHandler {
//    private final WebSocketHandler delegate;
//    private final Set<String> allowedOrigins;
//
//    public OriginCheckingWebSocketHandler(WebSocketHandler delegate, Set<String> allowedOrigins) {
//        this.delegate = delegate;
//        this.allowedOrigins = allowedOrigins;
//    }
//
//    @Override
//    public Mono<Void> handle(WebSocketSession session) {
//        String origin = session.getHandshakeInfo().getHeaders().getOrigin();
//        if (origin == null || !allowedOrigins.contains(origin)) {
//            return session.close(CloseStatus.POLICY_VIOLATION);
//        }
//        return delegate.handle(session);
//    }
//}
