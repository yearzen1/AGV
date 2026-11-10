package com.agv.backend.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimSocketHandlerTest {

    private final SimSocketHandler handler = new SimSocketHandler(new ObjectMapper());

    @Test
    @DisplayName("单个会话写入失败只移除该会话，不向调用方抛异常")
    void sendDropsBrokenSessionWithoutThrowing() throws Exception {
        WebSocketSession broken = session("broken", 1L);
        doThrow(new IllegalStateException(
                "The remote endpoint was in state [TEXT_PARTIAL_WRITING] which is an invalid state"))
                .when(broken).sendMessage(any());

        assertDoesNotThrow(() -> handler.afterConnectionEstablished(broken));
        assertDoesNotThrow(() -> handler.sendToUser(1L, Map.of("type", "frame")));
        assertEquals(0, handler.sessionCount(), "坏会话应被移除");
    }

    @Test
    @DisplayName("正常会话能收到连接确认与自己的仿真帧")
    void sendsToHealthySession() throws Exception {
        WebSocketSession healthy = session("healthy", 1L);

        handler.afterConnectionEstablished(healthy);
        handler.sendToUser(1L, Map.of("type", "frame"));

        verify(healthy, atLeast(2)).sendMessage(any());
        assertEquals(1, handler.sessionCount());
    }

    @Test
    @DisplayName("帧只发给目标用户，其他用户的连接收不到")
    void routesFramesPerUser() throws Exception {
        WebSocketSession first = session("first", 1L);
        WebSocketSession second = session("second", 2L);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        clearInvocations(first, second);
        handler.sendToUser(1L, Map.of("type", "frame"));

        verify(first, times(1)).sendMessage(any());
        verify(second, never()).sendMessage(any());
    }

    @Test
    @DisplayName("同一用户的多个标签页都能收到帧")
    void sendsToAllSessionsOfSameUser() throws Exception {
        WebSocketSession tabA = session("tab-a", 1L);
        WebSocketSession tabB = session("tab-b", 1L);

        handler.afterConnectionEstablished(tabA);
        handler.afterConnectionEstablished(tabB);
        handler.sendToUser(1L, Map.of("type", "frame"));

        verify(tabA, atLeast(2)).sendMessage(any());
        verify(tabB, atLeast(2)).sendMessage(any());
        assertEquals(1, handler.userCount());
    }

    @Test
    @DisplayName("连接关闭后会话被移除")
    void closedSessionIsRemoved() throws Exception {
        WebSocketSession session = session("s1", 1L);

        handler.afterConnectionEstablished(session);
        assertEquals(1, handler.sessionCount());

        handler.afterConnectionClosed(session, null);
        assertEquals(0, handler.sessionCount());
        assertEquals(0, handler.userCount());
    }

    @Test
    @DisplayName("没有 userId 的会话直接关闭，不注册")
    void sessionWithoutUserIsRejected() throws Exception {
        WebSocketSession anonymous = session("anonymous", null);

        handler.afterConnectionEstablished(anonymous);

        verify(anonymous).close(any(CloseStatus.class));
        assertEquals(0, handler.sessionCount());
    }

    private WebSocketSession session(String id, Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        if (userId != null) {
            attributes.put(JwtHandshakeInterceptor.ATTR_USER_ID, userId);
        }
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }
}
