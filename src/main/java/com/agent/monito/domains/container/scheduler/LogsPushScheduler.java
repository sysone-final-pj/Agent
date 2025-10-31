/**
 * 컨테이너 로그 수집 및 전송 스케줄러
 * 주기적으로 로그를 수집하여 WebSocket을 통해 Backend로 전송
 * since 파라미터를 사용하여 중복 방지
 */
package com.agent.monito.domains.container.scheduler;

import com.agent.monito.domains.agent.client.AgentWebSocketClient;
import com.agent.monito.domains.container.cache.ContainerLogTimestampCache;
import com.agent.monito.domains.container.dto.response.ContainerLogEntryResponseDTO;
import com.agent.monito.domains.container.service.ContainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogsPushScheduler {

    private final ContainerService containerService;
    private final AgentWebSocketClient webSocketClient;
    private final ContainerLogTimestampCache logTimestampCache;

    /**
     * 주기적으로 컨테이너 로그를 수집하여 WebSocket으로 전송
     * application.yml의 scheduler.logs-push 설정값을 따름
     */
    @Scheduled(
        fixedDelayString = "${scheduler.logs-push.fixed-delay}",
        initialDelayString = "${scheduler.logs-push.initial-delay}"
    )
    public void pushLogs() {
        // 연결 및 인증 상태 확인
        if (!webSocketClient.isReady()) {
            log.warn("WebSocket 연결 또는 인증 상태 아님. 로그 전송 생략.");
            return;
        }

        try {
            log.debug("컨테이너 로그 수집 시작...");

            // 캐시에서 각 컨테이너의 마지막 로그 수집 시점 가져오기
            Map<String, Integer> containerSinceMap = logTimestampCache.getAllTimestamps();
            log.debug("Using cached timestamps for {} containers", containerSinceMap.size());

            // 컨테이너 로그 수집
            // - 캐시에 없는 컨테이너 (첫 수집): 최근 10줄만
            // - 캐시에 있는 컨테이너: since 시점 이후의 로그만
            Map<String, List<ContainerLogEntryResponseDTO>> logs =
                containerService.collectAllContainerLogs(10, containerSinceMap);

            if (logs.isEmpty()) {
                log.debug("수집된 로그가 없습니다. 전송 생략.");
                return;
            }

            // 수집된 로그의 타임스탬프를 캐시에 업데이트
            int totalLogCount = 0;
            for (Map.Entry<String, List<ContainerLogEntryResponseDTO>> entry : logs.entrySet()) {
                String containerHash = entry.getKey();
                List<ContainerLogEntryResponseDTO> logEntries = entry.getValue();

                totalLogCount += logEntries.size();

                // 해당 컨테이너의 가장 최신 로그 타임스탬프 찾기
                logEntries.stream()
                    .filter(logEntry -> logEntry.getTimestamp() != null)
                    .forEach(logEntry ->
                        logTimestampCache.updateTimestamp(containerHash, logEntry.getTimestamp())
                    );

                // 로그가 없거나 타임스탬프가 모두 null인 경우 현재 시각으로 업데이트
                if (logEntries.isEmpty() ||
                    logEntries.stream().allMatch(log -> log.getTimestamp() == null)) {
                    logTimestampCache.updateToNow(containerHash);
                }
            }

            log.info("{}개 컨테이너에서 총 {}개의 새 로그 수집 완료. Backend로 전송 중...",
                    logs.size(), totalLogCount);

            // 수집 후 다시 연결 상태 확인 (race condition 방지)
            if (!webSocketClient.isReady()) {
                log.warn("로그 수집 중 연결이 끊어졌습니다. 다음 주기에 재시도합니다.");
                return;
            }

            // WebSocket으로 전송
            webSocketClient.sendLogsMessage(logs);

            log.info("✓ {}개 컨테이너의 {}개 로그 전송 완료", logs.size(), totalLogCount);

        } catch (IllegalStateException e) {
            log.error("WebSocket 세션이 닫혔습니다: {}", e.getMessage());
        } catch (Exception e) {
            log.error("로그 수집/전송 실패: {}", e.getMessage(), e);
        }
    }
}