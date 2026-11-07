package com.agv.backend.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仿真帧推送通道 {@code /ws/sim}。
 * <p>
 * W1 阶段只做 echo 与广播骨架；W6 起由仿真引擎每 100 ms 调用 {@link #broadcast(Object)} 推送一帧。
 */
@Component
public class SimSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public SimSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        session.sendMessage(new TextMessage(toJson(Map.of(
                "type", "connected",
                "sessionId", session.getId()))));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        session.sendMessage(new TextMessage(toJson(Map.of(
                "type", "echo",
                "payload", message.getPayload()))));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    /** 向所有已连接客户端推送一条消息。 */
    public void broadcast(Object payload) {
        String json = toJson(payload);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException ex) {
                sessions.remove(session);
            }
        }
    }

    public int sessionCount() {
        return sessions.size();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{\"type\":\"error\",\"message\":\"序列化失败\"}";
        }
    }
}
