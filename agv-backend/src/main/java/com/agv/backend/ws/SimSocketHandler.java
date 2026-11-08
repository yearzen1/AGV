package com.agv.backend.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仿真帧推送通道 {@code /ws/sim}。
 * <p>
 * 每个会话用 {@link ConcurrentWebSocketSessionDecorator} 包装，把同一连接上的发送串行化。
 * 没有这层包装时，广播线程与 echo 线程可能同时写同一个连接，触发
 * {@code TEXT_PARTIAL_WRITING} 并让整个仿真被判定为失败。
 * <p>
 * 单个会话发送失败只移除该会话，不向调用方抛异常。
 */
@Component
public class SimSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SimSocketHandler.class);

    /** 单次发送的最长等待时间。 */
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    /** 会话积压缓冲上限，超过后由装饰器关闭该会话而不是拖垮推送线程。 */
    private static final int BUFFER_SIZE_BYTES = 512 * 1024;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SimSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_BYTES);
        sessions.put(session.getId(), safe);
        send(safe, session.getId(),
                Map.of("type", "connected", "sessionId", session.getId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WebSocketSession safe = sessions.get(session.getId());
        if (safe == null) {
            safe = session;
        }
        send(safe, session.getId(),
                Map.of("type", "echo", "payload", message.getPayload()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    /** 向所有已连接客户端推送一条消息。 */
    public void broadcast(Object payload) {
        if (sessions.isEmpty()) {
            return;
        }
        String json = toJson(payload);
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (!session.isOpen()) {
                sessions.remove(entry.getKey());
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (Exception ex) {
                sessions.remove(entry.getKey());
                log.warn("推送失败，移除会话 {}：{}", entry.getKey(), ex.getMessage());
            }
        }
    }

    public int sessionCount() {
        return sessions.size();
    }

    private void send(WebSocketSession session, String sessionId, Object payload) {
        try {
            session.sendMessage(new TextMessage(toJson(payload)));
        } catch (Exception ex) {
            sessions.remove(sessionId);
            log.warn("会话 {} 写入失败，已移除：{}", sessionId, ex.getMessage());
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{\"type\":\"error\",\"message\":\"序列化失败\"}";
        }
    }
}
