package com.agv.backend.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimSocketHandlerTest {

    private final SimSocketHandler handler = new SimSocketHandler(new ObjectMapper());

    @Test
    @DisplayName("单个会话写入失败只移除该会话，不向调用方抛异常")
    void broadcastDropsBrokenSessionWithoutThrowing() throws Exception {
        WebSocketSession broken = mock(WebSocketSession.class);
        when(broken.getId()).thenReturn("broken");
        when(broken.isOpen()).thenReturn(true);
        doThrow(new IllegalStateException(
                "The remote endpoint was in state [TEXT_PARTIAL_WRITING] which is an invalid state"))
                .when(broken).sendMessage(any());

        assertDoesNotThrow(() -> handler.afterConnectionEstablished(broken));
        assertDoesNotThrow(() -> handler.broadcast(Map.of("type", "frame")));
        assertEquals(0, handler.sessionCount(), "坏会话应被移除");
    }

    @Test
    @DisplayName("正常会话能收到连接确认与仿真帧")
    void broadcastSendsToHealthySession() throws Exception {
        WebSocketSession healthy = mock(WebSocketSession.class);
        when(healthy.getId()).thenReturn("healthy");
        when(healthy.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(healthy);
        handler.broadcast(Map.of("type", "frame"));

        verify(healthy, atLeast(2)).sendMessage(any());
        assertEquals(1, handler.sessionCount());
    }

    @Test
    @DisplayName("连接关闭后会话被移除")
    void closedSessionIsRemoved() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        assertEquals(1, handler.sessionCount());

        handler.afterConnectionClosed(session, null);
        assertEquals(0, handler.sessionCount());
    }
}
