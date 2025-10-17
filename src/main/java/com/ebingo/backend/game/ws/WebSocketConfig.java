package com.ebingo.backend.game.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class WebSocketConfig {

    private final GameWebSocketHandler gameWebSocketHandler;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler) {
        this.gameWebSocketHandler = gameWebSocketHandler;
    }

    @Bean
    public SimpleUrlHandlerMapping handlerMapping() {
        Map<String, WebSocketHandler> urlMap = new HashMap<>();
        urlMap.put("/ws/game", originCheckingHandler(gameWebSocketHandler));

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(urlMap);
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    /**
     * Wraps the original WebSocketHandler with origin checking.
     */
    private WebSocketHandler originCheckingHandler(WebSocketHandler delegate) {
        return session -> {
            String origin = session.getHandshakeInfo().getHeaders().getFirst("Origin");

            // Allow these Origins (add yours here)
            if (origin == null ||
                    origin.equals("https://bingofam.com") ||
                    origin.equals("https://www.bingofam.com") ||
                    origin.equals("https://your-vercel-app.vercel.app") ||
                    origin.equals("http://localhost:3000")) {

                return delegate.handle(session);
            }

            System.out.println("WebSocket connection rejected due to invalid Origin: " + origin);
            return session.close();
        };
    }
}
