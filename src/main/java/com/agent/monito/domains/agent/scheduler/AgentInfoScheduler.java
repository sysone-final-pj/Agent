/**
 * Agent 정보 수집 및 전송 스케줄러
 * 인증 성공 시 1회 및 1시간마다 호스트 스펙을 Backend로 전송
 * BE의 InMemory 캐시 갱신용
 */
package com.agent.monito.domains.agent.scheduler;

import com.agent.monito.domains.agent.client.AgentWebSocketClient;
import com.agent.monito.domains.agent.collector.HostMemoryCollector;
import com.agent.monito.domains.agent.dto.response.AgentInfoResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/**
 작성자: 백승준
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentInfoScheduler {

    private final HostMemoryCollector hostMemoryCollector;
    private final AgentWebSocketClient webSocketClient;

    /**
     * 1시간마다 Agent 정보(호스트 스펙)를 Backend로 전송
     * 인증 성공 직후에도 AgentWebSocketClient.handleAuthSuccess()에서 수동 호출됨
     */
    @Scheduled(fixedDelayString = "${scheduler.agent-info-push.fixed-delay}")
    public void pushAgentInfo() {
        // 연결 및 인증 상태 확인
        if (!webSocketClient.isReady()) {
            log.debug("WebSocket 연결 또는 인증 상태 아님. Agent 정보 전송 생략.");
            return;
        }

        try {
            log.debug("Agent 정보 수집 시작...");

            // Agent 정보 수집
            AgentInfoResponseDTO agentInfo = hostMemoryCollector.collectAgentInfo();

            // 수집 후 다시 연결 상태 확인 (race condition 방지)
            if (!webSocketClient.isReady()) {
                log.warn("Agent 정보 수집 중 연결이 끊어졌습니다. 다음 주기에 재시도합니다.");
                return;
            }

            // WebSocket으로 전송
            webSocketClient.sendAgentInfoMessage(agentInfo);

            log.info("✓ Agent 정보 전송 완료 (Total Memory: {} bytes, CPU Cores: {}, Total Disk: {})",
                    agentInfo.getHostTotalMemory(), agentInfo.getHostCpuCores(), agentInfo.getHostTotalDisk());

        } catch (IllegalStateException e) {
            log.error("WebSocket 세션이 닫혔습니다: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Agent 정보 수집/전송 실패: {}", e.getMessage(), e);
        }
    }
}