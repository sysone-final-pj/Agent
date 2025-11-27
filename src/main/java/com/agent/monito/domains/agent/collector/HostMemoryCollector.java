/**
 * Agent가 설치된 호스트(VM)의 메모리 및 CPU 정보를 수집하는 클래스
 * Docker API를 사용하여 Docker가 실행되는 환경의 정보를 수집
 */
package com.agent.monito.domains.agent.collector;

import com.agent.monito.domains.agent.cache.DockerHostInfoCache;
import com.agent.monito.domains.agent.dto.response.AgentInfoResponseDTO;
import com.agent.monito.domains.agent.dto.response.HostDiskInfoResponseDTO;
import com.agent.monito.domains.agent.dto.response.HostMemoryInfoResponseDTO;
import com.github.dockerjava.api.model.Info;
import com.sun.management.OperatingSystemMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
/**
 작성자: 백승준
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HostMemoryCollector {

    private final DockerHostInfoCache dockerHostInfoCache;
    private final OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    @Value("${agent.key}")
    private String agentKey;

    /**
     * Agent 정보 수집 (연결 시 1회 전송용)
     * Docker가 실행되는 환경의 정보를 수집
     * @return Agent 정보 DTO (agentKey, hostTotalMemory, hostCpuCores, hostTotalDisk)
     */
    public AgentInfoResponseDTO collectAgentInfo() {
        try {
            Info dockerInfo = dockerHostInfoCache.getDockerHostInfo();

            long totalMemory = dockerInfo.getMemTotal();
            int cpuCores = dockerInfo.getNCPU();
            // Docker API는 디스크 정보를 직접 제공하지 않으므로 FileStore 사용
            long totalDisk = getTotalDiskSpace();

            log.info("Agent Info - Total Memory: {} bytes, CPU Cores: {}, Total Disk: {} bytes",
                    totalMemory, cpuCores, totalDisk);

            return AgentInfoResponseDTO.builder()
                    .agentKey(agentKey)
                    .hostTotalMemory(totalMemory)
                    .hostCpuCores(cpuCores)
                    .hostTotalDisk(totalDisk)
                    .build();

        } catch (Exception e) {
            log.error("Failed to collect agent info", e);
            return AgentInfoResponseDTO.builder()
                    .agentKey(agentKey)
                    .hostTotalMemory(0L)
                    .hostCpuCores(0)
                    .hostTotalDisk(0L)
                    .build();
        }
    }

    /**
     * 호스트(VM)의 메모리 정보를 수집 (메트릭 전송용)
     * 정적 정보(총 메모리)는 Docker API, 동적 정보(여유/사용 메모리)는 osBean 사용
     * @return 호스트 메모리 정보 DTO
     */
    public HostMemoryInfoResponseDTO collectHostMemory() {
        try {
            // Docker 환경 기준 전체 메모리
            long totalMemory = dockerHostInfoCache.getTotalMemory();
            // 실시간 여유 메모리 (osBean)
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

    /**
     * 호스트(VM)의 디스크 정보를 수집 (메트릭 전송용)
     * @return 호스트 디스크 정보 DTO
     */
    public HostDiskInfoResponseDTO collectHostDisk() {
        try {
            long totalDisk = 0;
            long availableDisk = 0;

            // 모든 파일 시스템의 디스크 정보를 합산
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                if (!store.isReadOnly()) {
                    totalDisk += store.getTotalSpace();
                    availableDisk += store.getUsableSpace();
                }
            }

            long usedDisk = totalDisk - availableDisk;

            log.debug("Host Disk - Total: {} bytes, Available: {} bytes, Used: {} bytes",
                    totalDisk, availableDisk, usedDisk);

            return HostDiskInfoResponseDTO.builder()
                    .totalDisk(totalDisk)
                    .availableDisk(availableDisk)
                    .usedDisk(usedDisk)
                    .build();

        } catch (Exception e) {
            log.error("Failed to collect host disk info", e);
            return HostDiskInfoResponseDTO.builder()
                    .totalDisk(0L)
                    .availableDisk(0L)
                    .usedDisk(0L)
                    .build();
        }
    }

    /**
     * 전체 디스크 용량 계산 (정적 정보 수집용)
     * @return 전체 디스크 용량 (bytes)
     */
    private long getTotalDiskSpace() {
        try {
            long totalDisk = 0;
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                if (!store.isReadOnly()) {
                    totalDisk += store.getTotalSpace();
                }
            }
            return totalDisk;
        } catch (Exception e) {
            log.error("Failed to get total disk space", e);
            return 0L;
        }
    }
}
