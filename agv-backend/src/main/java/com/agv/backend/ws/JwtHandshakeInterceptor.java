package com.agv.backend.ws;

import com.agv.backend.entity.SysUser;
import com.agv.backend.repository.SysUserRepository;
import com.agv.backend.security.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权：浏览器不能设置 Authorization 头，令牌从 {@code ?token=} 读取。
 * <p>
 * 校验通过后把 {@code userId} 写入会话属性，帧路由据此找到接收人。
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USERNAME = "username";

    private final JwtService jwtService;
    private final SysUserRepository userRepository;

    public JwtHandshakeInterceptor(JwtService jwtService, SysUserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            return reject(response);
        }
        Claims claims = jwtService.parse(token);
        if (claims == null || claims.getSubject() == null) {
            return reject(response);
        }
        Object uid = claims.get("uid");
        if (!(uid instanceof Number number)) {
            return reject(response);
        }
        SysUser user = userRepository.findByUsername(claims.getSubject()).orElse(null);
        if (user == null || !user.isEnabled() || !user.getId().equals(number.longValue())) {
            return reject(response);
        }
        attributes.put(ATTR_USER_ID, user.getId());
        attributes.put(ATTR_USERNAME, user.getUsername());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    private boolean reject(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }
}
