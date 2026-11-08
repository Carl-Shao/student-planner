package com.planner.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expireHours;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-hours}") long expireHours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {   // HS256 要求密钥至少 256 位
            throw new IllegalStateException("app.jwt.secret 至少需要 32 字节（当前 " + bytes.length + " 字节），请设置环境变量 JWT_SECRET");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireHours = expireHours;
    }

    public String create(long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireHours * 3600_000L))
                .signWith(key)
                .compact();
    }

    /** 解析失败返回 null */
    public Long parse(String token) {
        try {
            String sub = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getSubject();
            return Long.valueOf(sub);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
