/**
 * Spring Security 필터 체인을 설정하는 클래스
 * /api/health는 공개, 그 외 API는 JWT 인증을 요구하며 JwtAuthenticationFilter를 필터 체인에 등록함.
 */
package com.agent.monito.global.config;

import com.agent.monito.global.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // REST API에서는 CSRF 비활성화
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()   // 헬스체크는 공개
                        .anyRequest().authenticated()                 // 그 외는 인증 필요
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class); // JWT 필터 등록

        return http.build();
    }
}