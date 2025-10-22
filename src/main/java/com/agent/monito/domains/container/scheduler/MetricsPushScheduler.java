/**
 * [DEPRECATED] REST API 방식의 메트릭 전송 스케줄러
 *
 * 현재는 WebSocket 방식으로 전환되어 사용하지 않음.
 * AgentWebSocketClient가 동일한 기능을 WebSocket으로 수행함.
 *
 * 이 파일은 향후 제거 예정.
 */
package com.agent.monito.domains.container.scheduler;

import com.agent.monito.domains.container.dto.response.DetailedContainerMetricsResponseDTO;
import com.agent.monito.domains.container.service.ContainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

// @Component  // ← 비활성화: WebSocket 방식으로 전환됨
@Slf4j
@RequiredArgsConstructor
public class MetricsPushScheduler {

    private final ContainerService containerService;
    private final WebClient webClient;

    @Value("${backend.api.metrics}")
    private String metricsApiPath;

    /**
     * 주기적으로 컨테이너 메트릭을 수집하여 Backend로 전송
     * fixedDelayString: 이전 작업 완료 후 대기 시간 (application.yml에서 설정)
     * initialDelayString: 애플리케이션 시작 후 첫 실행까지 대기 시간 (application.yml에서 설정)
     */
    @Scheduled(
        fixedDelayString = "${scheduler.metrics-push.fixed-delay}",
        initialDelayString = "${scheduler.metrics-push.initial-delay}"
    )
    public void pushMetricsToBackend() {
        try {
            log.debug("Starting metrics collection for Backend push...");

            // 상세 메트릭 수집 (비동기 병렬 처리)
            List<DetailedContainerMetricsResponseDTO> metrics =
                containerService.collectAllDetailedContainerMetrics();

            if (metrics.isEmpty()) {
                log.debug("No running containers found. Skipping push.");
                return;
            }

            log.info("Collected {} container metrics. Pushing to Backend...", metrics.size());

            // Backend로 비동기 전송
            webClient.post()
                    .uri(metricsApiPath)
                    .bodyValue(metrics)
                    .retrieve()
                    .toBodilessEntity()
                    .doOnSuccess(response ->
                        log.info("✓ Successfully pushed {} metrics to Backend", metrics.size())
                    )
                    .doOnError(error ->
                        log.error("✗ Failed to push metrics to Backend: {}", error.getMessage())
                    )
                    .onErrorResume(error -> {
                        // 에러 발생 시에도 스케줄러는 계속 동작
                        log.warn("Backend communication error. Will retry in next schedule.");
                        return Mono.empty();
                    })
                    .subscribe(); // 비동기 실행

        } catch (Exception e) {
            log.error("Error during metrics collection: {}", e.getMessage(), e);
        }
    }
}
