package com.agv.backend.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SimSocketHandler simSocketHandler;

    public WebSocketConfig(SimSocketHandler simSocketHandler) {
        this.simSocketHandler = simSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(simSocketHandler, "/ws/sim")
                .setAllowedOriginPatterns("*");
    }
}
