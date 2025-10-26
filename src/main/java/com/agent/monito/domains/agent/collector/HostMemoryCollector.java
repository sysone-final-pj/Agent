/**
 * Agent가 설치된 호스트(VM)의 메모리 및 CPU 정보를 수집하는 클래스
 */
package com.agent.monito.domains.agent.collector;

import com.agent.monito.domains.agent.dto.response.AgentInfoResponseDTO;
import com.agent.monito.domains.agent.dto.response.HostMemoryInfoResponseDTO;
import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

@Component
@Slf4j
public class HostMemoryCollector {

    private final OperatingSystemMXBean osBean;

    @Value("${agent.key}")
    private String agentKey;

    public HostMemoryCollector() {
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    /**
     * Agent 정보 수집 (연결 시 1회 전송용)
     * @return Agent 정보 DTO (agentKey, hostTotalMemory, hostCpuCores)
     */
    public AgentInfoResponseDTO collectAgentInfo() {
        try {
            long totalMemory = osBean.getTotalMemorySize();
            int cpuCores = osBean.getAvailableProcessors();

            log.info("Agent Info - Total Memory: {} bytes, CPU Cores: {}", totalMemory, cpuCores);

            return AgentInfoResponseDTO.builder()
                    .agentKey(agentKey)
                    .hostTotalMemory(totalMemory)
                    .hostCpuCores(cpuCores)
                    .build();

        } catch (Exception e) {
            log.error("Failed to collect agent info", e);
            return AgentInfoResponseDTO.builder()
                    .agentKey(agentKey)
                    .hostTotalMemory(0L)
                    .hostCpuCores(0)
                    .build();
        }
    }

    /**
     * 호스트(VM)의 메모리 정보를 수집 (메트릭 전송용)
     * @return 호스트 메모리 정보 DTO
     */
    public HostMemoryInfoResponseDTO collectHostMemory() {
        try {
            long totalMemory = osBean.getTotalMemorySize();
            long freeMemory = osBean.getFreeMemorySize();
            long usedMemory = totalMemory - freeMemory;

            log.debug("Host Memory - Total: {} bytes, Free: {} bytes, Used: {} bytes",
                    totalMemory, freeMemory, usedMemory);

            return HostMemoryInfoResponseDTO.builder()
                    .totalMemory(totalMemory)
                    .availableMemory(freeMemory)
                    .usedMemory(usedMemory)
                    .build();

        } catch (Exception e) {
            log.error("Failed to collect host memory info", e);
            return HostMemoryInfoResponseDTO.builder()
                    .totalMemory(0L)
                    .availableMemory(0L)
                    .usedMemory(0L)
                    .build();
        }
    }
}
