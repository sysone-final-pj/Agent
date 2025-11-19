package com.agent.monito.domains.agent.client;

import com.agent.monito.domains.agent.collector.HostMemoryCollector;
import com.agent.monito.domains.agent.dto.response.AgentInfoResponseDTO;
import com.agent.monito.domains.container.cache.ContainerStateCache;
import com.agent.monito.domains.container.collector.ContainerSnapshotCollector;
import com.agent.monito.domains.container.dto.response.ContainerLogEntryResponseDTO;
import com.agent.monito.domains.container.dto.response.DetailedContainerMetricsResponseDTO;
import com.agent.monito.domains.container.state.ContainerSnapshot;
import com.agent.monito.domains.container.state.ContainerStateChange;
import com.agent.monito.domains.container.state.StateChangeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private final HostMemoryCollector hostMemoryCollector;
    private final ContainerStateCache containerStateCache;
    private final ContainerSnapshotCollector containerSnapshotCollector;
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
            String wsUrl = backendUrl;

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

            case "LOGS_ACK":
                log.debug("container 로그 수신");
                break;

            case "AGENT_INFO_ACK":
                log.debug("Agent 메타데이터 ACK 수신");
                break;

            case "CONTAINER_SYNC_ACK":
                log.debug("컨테이너 동기화 ACK 수신");
                break;

            case "CONTAINER_STATE_CHANGE_ACK":
                log.debug("컨테이너 상태 변경 ACK 수신");
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
        sendInitialAgentInfo();

        // 전체 컨테이너 상태 동기화 (초기 동기화)
        syncAllContainerStates();
    }

    private void handleAuthFailed(Map<String, Object> data) {
        log.error("═══════════════════════════════════════");
        log.error("인증 실패!");
        log.error("   이유: {}", data.get("message"));
        log.error("   연결이 곧 종료됩니다.");
        log.error("═══════════════════════════════════════");
    }

    /**
     * 인증 성공 직후 Agent 정보를 즉시 전송
     */
    private void sendInitialAgentInfo() {
        try {
            AgentInfoResponseDTO agentInfo = hostMemoryCollector.collectAgentInfo();
            sendAgentInfoMessage(agentInfo);
            log.info("✓ Agent 정보 전송 완료 (Total Memory: {} bytes, CPU Cores: {}, Total Disk: {})",
                    agentInfo.getHostTotalMemory(), agentInfo.getHostCpuCores(), agentInfo.getHostTotalDisk());
        } catch (Exception e) {
            log.error("Agent 정보 전송 실패: {}", e.getMessage(), e);
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

    /**
     * 연결 및 인증 상태 확인
     */
    private boolean isConnectedAndAuthenticated() {
        return session != null && session.isOpen() && authenticated;
    }

    /**
     * WebSocket 메시지 전송 (동기화)
     * 전송 직전에 연결 상태를 다시 확인하여 race condition 방지
     */
    private synchronized void sendWebSocketMessage(TextMessage message) throws Exception {
        if (session == null || !session.isOpen()) {
            throw new IllegalStateException("WebSocket session is not open");
        }
        session.sendMessage(message);
    }

    /**
     * 연결 에러 처리
     */
    private void handleConnectionError() {
        authenticated = false;
        log.warn("연결 문제 감지. 인증 상태 초기화 및 재연결 예약.");
        scheduleReconnect();
    }

    // ========== Public API for Schedulers ==========

    /**
     * 메트릭 데이터 전송 (Scheduler에서 호출)
     */
    public void sendMetricsMessage(List<DetailedContainerMetricsResponseDTO> metrics) throws Exception {
        if (!isConnectedAndAuthenticated()) {
            throw new IllegalStateException("Not connected or authenticated");
        }

        Map<String, Object> message = Map.of(
                "type", "METRICS",
                "data", Map.of(
                        "agentKey", agentKey,
                        "metrics", metrics,
                        "timestamp", System.currentTimeMillis()
                )
        );

        String json = objectMapper.writeValueAsString(message);
        log.debug("전송할 JSON (메트릭): {}", json);
        sendWebSocketMessage(new TextMessage(json));
    }

    /**
     * 로그 데이터 전송 (Scheduler에서 호출)
     */
    public void sendLogsMessage(Map<String, List<ContainerLogEntryResponseDTO>> logs) throws Exception {
        if (!isConnectedAndAuthenticated()) {
            throw new IllegalStateException("Not connected or authenticated");
        }

        Map<String, Object> message = Map.of(
                "type", "LOGS",
                "data", Map.of(
                        "agentKey", agentKey,
                        "logs", logs,
                        "timestamp", System.currentTimeMillis()
                )
        );

        String json = objectMapper.writeValueAsString(message);
        log.debug("전송할 JSON (로그): {}", json);
        sendWebSocketMessage(new TextMessage(json));
    }

    /**
     * Agent 정보 전송 (Scheduler에서 호출)
     */
    public void sendAgentInfoMessage(AgentInfoResponseDTO agentInfo) throws Exception {
        if (!isConnectedAndAuthenticated()) {
            throw new IllegalStateException("Not connected or authenticated");
        }

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
        sendWebSocketMessage(new TextMessage(json));
    }

    /**
     * 컨테이너 상태 변경 메시지 전송 (Scheduler에서 호출)
     */
    public void sendContainerStateChangeMessage(List<ContainerSnapshot> containers) throws Exception {
        if (!isConnectedAndAuthenticated()) {
            throw new IllegalStateException("Not connected or authenticated");
        }

        if (containers == null || containers.isEmpty()) {
            log.debug("상태 변경된 컨테이너가 없습니다.");
            return;
        }

        Map<String, Object> message = Map.of(
                "type", "CONTAINER_STATE_CHANGE",
                "data", Map.of(
                        "agentKey", agentKey,
                        "containers", containers,
                        "timestamp", System.currentTimeMillis()
                )
        );

        String json = objectMapper.writeValueAsString(message);
        log.debug("전송할 JSON (컨테이너 상태 변경): {}", json);

        sendWebSocketMessage(new TextMessage(json));
    }

    /**
     * 초기 컨테이너 동기화 메시지 전송 (인증 성공 직후 한 번만 호출)
     */
    private void sendContainerSyncMessage(List<ContainerSnapshot> containers) throws Exception {
        if (!isConnectedAndAuthenticated()) {
            throw new IllegalStateException("Not connected or authenticated");
        }

        // 컨테이너가 없어도 서버에 빈 배열을 전송하여 상태를 알림
        if (containers == null) {
            containers = List.of();
        }

        Map<String, Object> message = Map.of(
                "type", "CONTAINER_SYNC",
                "data", Map.of(
                        "agentKey", agentKey,
                        "containers", containers,
                        "timestamp", System.currentTimeMillis()
                )
        );

        String json = objectMapper.writeValueAsString(message);
        log.debug("전송할 JSON (컨테이너 동기화): {}", json);

        sendWebSocketMessage(new TextMessage(json));
    }

    /**
     * 초기 컨테이너 상태 동기화 (인증 성공 직후 호출)
     * 모든 컨테이너의 현재 상태를 BE에 전송하고 캐시에 저장
     */
    private void syncAllContainerStates() {
        try {
            log.info("═══════════════════════════════════════");
            log.info("컨테이너 상태 초기 동기화 시작...");

            // 모든 컨테이너 상태 수집 (실행 중 + 종료됨)
            List<ContainerSnapshot> allContainers =
                    containerSnapshotCollector.collectAllContainerSnapshots();

            // CONTAINER_SYNC 메시지 전송 (빈 배열도 전송하여 서버에 상태 알림)
            sendContainerSyncMessage(allContainers);

            // 캐시에 저장 (다음 상태 변화 감지를 위해)
            containerStateCache.updateStates(allContainers);

            if (allContainers.isEmpty()) {
                log.info("✓ 컨테이너 상태 동기화 완료 (컨테이너 없음)");
            } else {
                log.info("✓ 컨테이너 상태 동기화 완료 ({}개)", allContainers.size());
                log.info("   - 컨테이너 목록:");
                for (ContainerSnapshot snapshot : allContainers) {
                    log.info("     • {} ({})", snapshot.getContainerName(), snapshot.getState());
                }
            }
            log.info("═══════════════════════════════════════");

        } catch (Exception e) {
            log.error("컨테이너 상태 동기화 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 컨테이너 상태 변화 감지 및 전송 (Scheduler에서 호출)
     */
    public void detectAndSendStateChanges() {
        try {
            if (!isConnectedAndAuthenticated()) {
                log.debug("연결되지 않음. 상태 변화 감지 스킵.");
                return;
            }

            // 현재 모든 컨테이너 상태 수집
            List<ContainerSnapshot> currentContainers =
                    containerSnapshotCollector.collectAllContainerSnapshots();

            // 이전 상태와 비교하여 변화 감지
            StateChangeResult changes = containerStateCache.detectChanges(currentContainers);

            // 상태 변경 메시지 전송
            List<ContainerSnapshot> changedContainers = new ArrayList<>();

            // 새로 생성된 컨테이너
            if (!changes.getNewContainers().isEmpty()) {
                log.info("새로 생성된 컨테이너: {}", changes.getNewContainers().size());
                changedContainers.addAll(changes.getNewContainers());
            }

            // 종료된 컨테이너
            if (!changes.getStoppedContainers().isEmpty()) {
                log.info("종료된 컨테이너: {}", changes.getStoppedContainers().size());
                changedContainers.addAll(changes.getStoppedContainers());
            }

            // 상태가 변경된 컨테이너
            if (!changes.getStateChanges().isEmpty()) {
                log.info("상태 변경된 컨테이너: {}", changes.getStateChanges().size());
                for (ContainerStateChange change : changes.getStateChanges()) {
                    // 현재 컨테이너 정보에서 전체 데이터 찾기 (status 포함)
                    ContainerSnapshot fullSnapshot = currentContainers.stream()
                            .filter(c -> c.getContainerHash().equals(change.getContainerHash()))
                            .findFirst()
                            .orElse(ContainerSnapshot.builder()
                                    .containerHash(change.getContainerHash())
                                    .containerName(change.getContainerName())
                                    .state(change.getNewState())
                                    .status(change.getNewStatus())
                                    .build());
                    changedContainers.add(fullSnapshot);
                }
            }

            // 변화가 있으면 메시지 전송
            if (!changedContainers.isEmpty()) {
                sendContainerStateChangeMessage(changedContainers);
                log.info("✓ 컨테이너 상태 변경 메시지 전송 완료 ({}개)", changedContainers.size());
            }

            // 캐시 업데이트
            containerStateCache.updateStates(currentContainers);

        } catch (Exception e) {
            log.error("컨테이너 상태 변화 감지 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 연결 및 인증 상태 확인 (Scheduler에서 호출)
     */
    public boolean isReady() {
        return isConnectedAndAuthenticated();
    }
}
