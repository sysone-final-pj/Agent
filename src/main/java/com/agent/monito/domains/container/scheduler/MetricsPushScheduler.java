/**
 * 컨테이너 메트릭 수집 및 전송 스케줄러
 * 주기적으로 메트릭을 수집하여 WebSocket을 통해 Backend로 전송
 * 메트릭 수집 과정에서 컨테이너 상태 변화도 함께 감지하여 전송
 */
package com.agent.monito.domains.container.scheduler;

import com.agent.monito.domains.agent.client.AgentWebSocketClient;
import com.agent.monito.domains.container.dto.response.DetailedContainerMetricsResponseDTO;
import com.agent.monito.domains.container.service.ContainerService;
import com.agent.monito.domains.container.stream.ContainerStatsStreamManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsPushScheduler {

    private final ContainerService containerService;
    private final AgentWebSocketClient webSocketClient;
    private final ContainerStatsStreamManager streamManager;

    /**
     * 주기적으로 컨테이너 메트릭을 수집하여 WebSocket으로 전송
     * 메트릭 수집 전에 상태 변화를 감지하여 함께 처리
     * application.yml의 scheduler.metrics-push 설정값을 따름
     */
    @Scheduled(
        fixedDelayString = "${scheduler.metrics-push.fixed-delay}",
        initialDelayString = "${scheduler.metrics-push.initial-delay}"
    )
    public void pushMetrics() {
        // 연결 및 인증 상태 확인
        if (!webSocketClient.isReady()) {
            log.warn("WebSocket 연결 또는 인증 상태 아님. 메트릭 전송 생략.");
            return;
        }

        try {
            log.debug("컨테이너 메트릭 수집 시작...");

            // 0. 스트림 동기화: 새 컨테이너 감지 및 스트림 시작/종료
            streamManager.syncWithRunningContainers();

            // 1. 메트릭 수집 전: 상태 변화 감지 (컨테이너 생성/종료/상태 변경)
            webSocketClient.detectAndSendStateChanges();

            // 2. 컨테이너 메트릭 수집 (StreamManager 캐시에서 조회)
            List<DetailedContainerMetricsResponseDTO> metrics =
                containerService.collectAllDetailedContainerMetrics();

            if (metrics.isEmpty()) {
                log.debug("실행 중인 컨테이너가 없습니다. 메트릭 전송 생략.");
                return;
            }

            log.info("{}개의 컨테이너 메트릭 수집 완료 (활성 스트림: {}개). Backend로 전송 중...",
                    metrics.size(), streamManager.getActiveStreamCount());

            // 3. 수집 후 다시 연결 상태 확인 (race condition 방지)
            if (!webSocketClient.isReady()) {
                log.warn("메트릭 수집 중 연결이 끊어졌습니다. 다음 주기에 재시도합니다.");
                return;
            }

            // 4. WebSocket으로 메트릭 전송
            webSocketClient.sendMetricsMessage(metrics);

            log.info("✓ {}개의 컨테이너 메트릭 전송 완료", metrics.size());

        } catch (IllegalStateException e) {
            log.error("WebSocket 세션이 닫혔습니다: {}", e.getMessage());
        } catch (Exception e) {
            log.error("메트릭 수집/전송 실패: {}", e.getMessage(), e);
        }
    }
}