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
 * 仿真帧推送通道 {@code /ws/sim?token=<jwt>}。
 * <p>
 * 会话按用户分组：仿真帧只发给发起人，不广播给所有人。
 * 每个会话用 {@link ConcurrentWebSocketSessionDecorator} 包装，把同一连接上的发送串行化。
 */
@Component
public class SimSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SimSocketHandler.class);

    /** 单次发送的最长等待时间。 */
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    /** 会话积压缓冲上限，超过后由装饰器关闭该会话而不是拖垮推送线程。 */
    private static final int BUFFER_SIZE_BYTES = 512 * 1024;

    private final Map<Long, Map<String, WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SimSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userIdOf(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_BYTES);
        sessionsByUser.computeIfAbsent(userId, key -> new ConcurrentHashMap<>())
                .put(session.getId(), safe);
        send(safe, session.getId(), Map.of("type", "connected", "sessionId", session.getId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WebSocketSession safe = session;
        Long userId = userIdOf(session);
        if (userId != null) {
            Map<String, WebSocketSession> userSessions = sessionsByUser.get(userId);
            if (userSessions != null && userSessions.containsKey(session.getId())) {
                safe = userSessions.get(session.getId());
            }
        }
        send(safe, session.getId(), Map.of("type", "echo", "payload", message.getPayload()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId == null) {
            return;
        }
        Map<String, WebSocketSession> userSessions = sessionsByUser.get(userId);
        if (userSessions == null) {
            return;
        }
        userSessions.remove(session.getId());
        if (userSessions.isEmpty()) {
            sessionsByUser.remove(userId, userSessions);
        }
    }

    /** 只向指定用户的全部连接推送；没有连接时直接返回。 */
    public void sendToUser(Long userId, Object payload) {
        Map<String, WebSocketSession> userSessions = sessionsByUser.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }
        String json = toJson(payload);
        for (Map.Entry<String, WebSocketSession> entry : userSessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (!session.isOpen()) {
                userSessions.remove(entry.getKey());
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (Exception ex) {
                userSessions.remove(entry.getKey());
                log.warn("推送失败，移除会话 {}：{}", entry.getKey(), ex.getMessage());
            }
        }
        if (userSessions.isEmpty()) {
            sessionsByUser.remove(userId, userSessions);
        }
    }

    public int sessionCount() {
        return sessionsByUser.values().stream().mapToInt(Map::size).sum();
    }

    public int userCount() {
        return sessionsByUser.size();
    }

    private Long userIdOf(WebSocketSession session) {
        Object value = session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_ID);
        return value instanceof Number number ? number.longValue() : null;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ex) {
            log.debug("关闭未鉴权会话失败：{}", ex.getMessage());
        }
    }

    private void send(WebSocketSession session, String sessionId, Object payload) {
        try {
            session.sendMessage(new TextMessage(toJson(payload)));
        } catch (Exception ex) {
            Long userId = userIdOf(session);
            if (userId != null) {
                Map<String, WebSocketSession> userSessions = sessionsByUser.get(userId);
                if (userSessions != null) {
                    userSessions.remove(sessionId);
                }
            }
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
