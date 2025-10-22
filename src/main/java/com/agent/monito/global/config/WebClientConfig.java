/**
 * Backend 서버 연동을 위한 WebClient 설정 클래스
 * .env 파일의 BE_HOST 값을 읽어서 WebClient Bean을 생성함.
 */
package com.agent.monito.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@Slf4j
public class WebClientConfig {

    @Value("${backend.host.url}")
    private String backendHost;

    @Bean
    public WebClient webClient() {
        // HTTP 클라이언트 설정 (타임아웃 등)
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(5))  // 응답 타임아웃 5초
                .doOnConnected(conn ->
                    conn.addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(5))
                        .addHandlerLast(new io.netty.handler.timeout.WriteTimeoutHandler(5))
                );

        WebClient client = WebClient.builder()
                .baseUrl(backendHost)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        log.info("Backend WebClient Configuration Loaded:");
        log.info("   • Backend Host : {}", backendHost);
        log.info("WebClient successfully initialized for Backend communication");

        return client;
    }
}
