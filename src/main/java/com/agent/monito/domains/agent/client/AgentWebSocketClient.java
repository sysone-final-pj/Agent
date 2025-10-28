package com.agent.monito.domains.agent.client;

import com.agent.monito.domains.agent.collector.HostMemoryCollector;
import com.agent.monito.domains.agent.dto.response.AgentInfoResponseDTO;
import com.agent.monito.domains.container.dto.response.DetailedContainerMetricsResponseDTO;
import com.agent.monito.domains.container.service.ContainerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentWebSocketClient extends TextWebSocketHandler {

    private final ContainerService containerService;
    private final HostMemoryCollector hostMemoryCollector;
    private final ObjectMapper objectMapper;

    @Value("${agent.key}")
    private String agentKey;

    @Value("${backend.websocket.url}")
    private String backendUrl;

    private WebSocketSession session;
    private boolean authenticated = false;

    @PostConstruct
    public void init() {
        log.info("═══════════════════════════════════════");
        log.info("Agent 시작");
        log.info("   Agent Key: {}", agentKey);
        log.info("   Backend URL: {}", backendUrl);
        log.info("═══════════════════════════════════════");

        connect();
    }

    private void connect() {
        try {
            StandardWebSocketClient client = new StandardWebSocketClient();
            String wsUrl = backendUrl + "/ws/agent";

            log.info("Backend 연결 시도...");
            log.info("   URL: {}", wsUrl);

            session = client.execute(this, wsUrl).get();

        } catch (Exception e) {
            log.error("연결 실패", e);
            scheduleReconnect();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        this.session = session;

        log.info("═══════════════════════════════════════");
        log.info("Backend 연결 성공!");
        log.info("   Session ID: {}", session.getId());
        log.info("   시각: {}", getCurrentTime());
        log.info("═══════════════════════════════════════");

        // 즉시 인증 시도
        authenticate();
    }

    private void authenticate() {
        try {
            Map<String, Object> authMsg = Map.of(
                    "type", "AUTH",
                    "agentKey", agentKey,
                    "timestamp", System.currentTimeMillis()
            );

            String json = objectMapper.writeValueAsString(authMsg);
            session.sendMessage(new TextMessage(json));

            log.info("인증 요청 전송");
            log.info("   Agent Key: {}", agentKey);

        } catch (Exception e) {
            log.error("인증 요청 실패", e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("Backend로부터 메시지 수신: {}", payload);

        Map<String, Object> data = objectMapper.readValue(payload, Map.class);
        String type = (String) data.get("type");

        switch (type) {
            case "SYSTEM":
                log.info("시스템 메시지: {}", data.get("message"));
                break;

            case "AUTH_SUCCESS":
                handleAuthSuccess(data);
                break;

            case "AUTH_FAILED":
                handleAuthFailed(data);
                break;

            case "ACK":
                log.debug("메트릭 ACK 수신");
                break;

            case "AGENT_INFO_ACK":
                log.debug("Agent 메타데이터 수신");
                break;

            case "PONG":
                log.debug("PONG 수신");
                break;

            case "ERROR":
                log.error("에러 메시지: {}", data.get("message"));
                break;

            default:
                log.warn("알 수 없는 메시지 타입: {}", type);
        }
    }

    private void handleAuthSuccess(Map<String, Object> data) {
        authenticated = true;

        log.info("═══════════════════════════════════════");
        log.info("인증 성공!");
        log.info("   Agent Key: {}", data.get("agentKey"));
        log.info("   메시지: {}", data.get("message"));
        log.info("   Agent 정보 전송 시작...");
        log.info("═══════════════════════════════════════");

        // 인증 성공 직후 Agent 정보 전송
        sendAgentInfo();
    }

    private void handleAuthFailed(Map<String, Object> data) {
        log.error("═══════════════════════════════════════");
        log.error("인증 실패!");
        log.error("   이유: {}", data.get("message"));
        log.error("   연결이 곧 종료됩니다.");
        log.error("═══════════════════════════════════════");
    }

    /**
     * 인증 성공 시 1회 및 1시간마다 Agent 정보(호스트 스펙)를 Backend로 전송
     * BE의 InMemory 캐시 갱신용
     */
    @Scheduled(fixedRate = 3600000) // 1시간 = 3600000ms
    public void sendAgentInfo() {
        if (session == null || !session.isOpen() || !authenticated) {
            log.debug("Agent 정보 전송 생략 (연결 또는 인증 상태 아님)");
            return;
        }

        try {
            AgentInfoResponseDTO agentInfo = hostMemoryCollector.collectAgentInfo();

            // BE가 기대하는 nested 구조로 전송
            Map<String, Object> message = Map.of(
                    "type", "AGENT_INFO",
                    "data", Map.of(
                            "agentKey", agentKey,
                            "host", Map.of(
                                    "totalMemory", agentInfo.getHostTotalMemory(),
                                    "cpuCores", agentInfo.getHostCpuCores(),
                                    "totalDisk", agentInfo.getHostTotalDisk()
                            )
                    )
            );

            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));

            log.info("✓ Agent 정보 전송 완료 (Total Memory: {} bytes, CPU Cores: {} Total Disk: {})",
                    agentInfo.getHostTotalMemory(), agentInfo.getHostCpuCores(), agentInfo.getHostTotalDisk());

        } catch (Exception e) {
            log.error("Agent 정보 전송 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 주기적으로 실제 컨테이너 메트릭을 수집하여 WebSocket으로 전송
     * application.yml의 scheduler.metrics-push 설정값을 따름
     */
    @Scheduled(
        fixedDelayString = "${scheduler.metrics-push.fixed-delay}",
        initialDelayString = "${scheduler.metrics-push.initial-delay}"
    )
    public void sendMetrics() {
        if (session == null || !session.isOpen()) {
            log.warn("연결되지 않음. 재연결 시도...");
            connect();
            return;
        }

        if (!authenticated) {
            log.warn("인증되지 않음. 메트릭 전송 불가.");
            return;
        }

        try {
            log.debug("컨테이너 메트릭 수집 시작...");

            // 실제 컨테이너 메트릭 수집 (호스트 정보 제외)
            List<DetailedContainerMetricsResponseDTO> metrics =
                containerService.collectAllDetailedContainerMetrics();

            if (metrics.isEmpty()) {
                log.debug("실행 중인 컨테이너가 없습니다. 전송 생략.");
                return;
            }

            log.info("{}개의 컨테이너 메트릭 수집 완료. Backend로 전송 중...", metrics.size());

            // WebSocket으로 메트릭 전송 (metrics)
            Map<String, Object> message = Map.of(
                    "type", "METRICS",
                    "data", Map.of(
                            "agentKey", agentKey,
                            "metrics", metrics,
                            "timestamp", System.currentTimeMillis()
                    )
            );

            String json = objectMapper.writeValueAsString(message);
            log.debug("전송할 JSON: {}", json);  // 디버그용 로그 추가
            session.sendMessage(new TextMessage(json));

            log.info("✓ {}개의 컨테이너 메트릭 전송 완료", metrics.size());

        } catch (Exception e) {
            log.error("메트릭 수집/전송 실패: {}", e.getMessage(), e);
            // 연결 문제일 수 있으므로 인증 상태 초기화
            if (e.getMessage() != null && e.getMessage().contains("connection")) {
                authenticated = false;
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("═══════════════════════════════════════");
        log.warn("연결 종료");
        log.warn("   Status: {}", status);
        log.warn("   시각: {}", getCurrentTime());
        log.info("═══════════════════════════════════════");

        authenticated = false;
        scheduleReconnect();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("═══════════════════════════════════════");
        log.error("전송 에러 발생");
        log.error("   Error: ", exception);
        log.error("═══════════════════════════════════════");
    }

    private void scheduleReconnect() {
        log.info("5초 후 재연결 시도...");

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            log.info("재연결 시도 중...");
            connect();
        }, 5, TimeUnit.SECONDS);
    }

    private String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
