/**
 * 컨테이너 메트릭 수집 및 전송 스케줄러
 * 주기적으로 메트릭을 수집하여 WebSocket을 통해 Backend로 전송
 */
package com.agent.monito.domains.container.scheduler;

import com.agent.monito.domains.agent.client.AgentWebSocketClient;
import com.agent.monito.domains.container.dto.response.DetailedContainerMetricsResponseDTO;
import com.agent.monito.domains.container.service.ContainerService;
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

    /**
     * 주기적으로 컨테이너 메트릭을 수집하여 WebSocket으로 전송
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

            // 컨테이너 메트릭 수집
            List<DetailedContainerMetricsResponseDTO> metrics =
                containerService.collectAllDetailedContainerMetrics();

            if (metrics.isEmpty()) {
                log.debug("실행 중인 컨테이너가 없습니다. 전송 생략.");
                return;
            }

            log.info("{}개의 컨테이너 메트릭 수집 완료. Backend로 전송 중...", metrics.size());

            // 수집 후 다시 연결 상태 확인 (race condition 방지)
            if (!webSocketClient.isReady()) {
                log.warn("메트릭 수집 중 연결이 끊어졌습니다. 다음 주기에 재시도합니다.");
                return;
            }

            // WebSocket으로 전송
            webSocketClient.sendMetricsMessage(metrics);

            log.info("✓ {}개의 컨테이너 메트릭 전송 완료", metrics.size());

        } catch (IllegalStateException e) {
            log.error("WebSocket 세션이 닫혔습니다: {}", e.getMessage());
        } catch (Exception e) {
            log.error("메트릭 수집/전송 실패: {}", e.getMessage(), e);
        }
    }
}