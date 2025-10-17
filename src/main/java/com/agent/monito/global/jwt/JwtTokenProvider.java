/**
 * JWT 토큰의 생성, 파싱, 검증 로직을 담당하는 유틸리티 클래스
 * 비밀키를 .env에서 로드하며 토큰의 유효기간 및 서명 검증을 수행함.
 */
package com.agent.monito.global.jwt;

import com.agent.monito.global.exception.ConfigurationException;
import com.agent.monito.global.exception.ExceptionMessage;
import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final String secretKey;
    private String token;

    public JwtTokenProvider() {
        Dotenv dotenv = Dotenv.load();
        this.secretKey = dotenv.get("JWT_SECRET");

        if (this.secretKey == null || this.secretKey.isBlank()) {
            log.error("Missing required environment variable: JWT_SECRET");
            throw new ConfigurationException(ExceptionMessage.INVALID_JWT_CONFIGURATION);
        }

        log.debug("JWT secret key successfully loaded from .env");
    }

    // 애플리케이션 시작 시 1회 토큰 생성
    @PostConstruct
    public void initToken() {
        try {
            this.token = createToken("central-backend", 3600_000L);
            log.info("JWT token generated successfully at startup (subject='central-backend', expires in 1h)");
            log.debug("Generated token: {}", this.token); // TODO :개발 단계에서만 노출, 추후 application.yml 설정 변경!
        } catch (Exception e) {
            log.error("Failed to initialize JWT token: {}", e.getMessage());
        }
    }

    public String getToken() {
        return token;
    }

    // JWT 토큰 생성
    public String createToken(String subject, long expireMillis) {
        Key key = Keys.hmacShaKeyFor(secretKey.getBytes());
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireMillis);

        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // JWT 토큰 검증
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            boolean valid = !claims.getExpiration().before(new Date());
            if (!valid) log.warn("Token expired at {}", claims.getExpiration());
            return valid;
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            return false;
        }
    }

    // 토큰의 Payload(Claims) 추출
    public Claims getClaims(String token) {
        Key key = Keys.hmacShaKeyFor(secretKey.getBytes());
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}