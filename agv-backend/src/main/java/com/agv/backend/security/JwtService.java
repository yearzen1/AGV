package com.agv.backend.security;

import com.agv.backend.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expireMillis;

    public JwtService(@Value("${agv.jwt.secret}") String secret,
                      @Value("${agv.jwt.expire-hours:12}") long expireHours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("agv.jwt.secret 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMillis = expireHours * 3600_000L;
    }

    public String issue(SysUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /** 解析失败返回 null，由过滤器决定是否放行。 */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
