package com.agv.backend.ws;

import com.agv.backend.entity.SysUser;
import com.agv.backend.repository.SysUserRepository;
import com.agv.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtHandshakeInterceptorTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-0123456789";

    private JwtService jwtService;
    private SysUserRepository userRepository;
    private JwtHandshakeInterceptor interceptor;
    private SysUser user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 1);
        userRepository = mock(SysUserRepository.class);
        interceptor = new JwtHandshakeInterceptor(jwtService, userRepository);
        user = new SysUser();
        user.setId(7L);
        user.setUsername("dispatcher");
        user.setRole(SysUser.ROLE_DISPATCHER);
        user.setStatus(1);
        when(userRepository.findByUsername("dispatcher")).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("合法令牌：握手通过并把 userId 写入会话属性")
    void validTokenPasses() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        MockHttpServletResponse raw = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                request("token=" + jwtService.issue(user)), response(raw),
                mock(WebSocketHandler.class), attributes);

        assertTrue(allowed);
        assertEquals(7L, attributes.get(JwtHandshakeInterceptor.ATTR_USER_ID));
        assertEquals("dispatcher", attributes.get(JwtHandshakeInterceptor.ATTR_USERNAME));
    }

    @Test
    @DisplayName("缺少令牌：拒绝握手并返回 401")
    void missingTokenIsRejected() throws Exception {
        MockHttpServletResponse raw = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                request(null), response(raw), mock(WebSocketHandler.class), new HashMap<>());

        assertFalse(allowed);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), raw.getStatus());
    }

    @Test
    @DisplayName("非法令牌：拒绝握手并返回 401")
    void invalidTokenIsRejected() throws Exception {
        MockHttpServletResponse raw = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                request("token=not-a-token"), response(raw),
                mock(WebSocketHandler.class), new HashMap<>());

        assertFalse(allowed);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), raw.getStatus());
    }

    @Test
    @DisplayName("账号已停用：拒绝握手")
    void disabledUserIsRejected() throws Exception {
        user.setStatus(0);
        MockHttpServletResponse raw = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                request("token=" + jwtService.issue(user)), response(raw),
                mock(WebSocketHandler.class), new HashMap<>());

        assertFalse(allowed);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), raw.getStatus());
    }

    private ServerHttpRequest request(String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/sim");
        request.setQueryString(query);
        return new ServletServerHttpRequest(request);
    }

    private ServerHttpResponse response(MockHttpServletResponse raw) {
        return new ServletServerHttpResponse(raw);
    }
}
