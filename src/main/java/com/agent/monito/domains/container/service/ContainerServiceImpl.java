package com.agent.monito.domains.container.service;

import com.agent.monito.domains.agent.collector.HostMemoryCollector;
import com.agent.monito.domains.agent.dto.response.HostMemoryInfoResponseDTO;
import com.agent.monito.domains.container.collector.ContainerMetricsCollector;
import com.agent.monito.domains.container.dto.response.ContainerMetricsResponseDTO;
import com.agent.monito.domains.container.dto.response.DetailedContainerMetricsResponseDTO;
import com.agent.monito.domains.container.dto.response.MetricsWithHostInfoResponseDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContainerServiceImpl implements ContainerService{

    private final ContainerMetricsCollector containerMetricsCollector;
    private final HostMemoryCollector hostMemoryCollector;

    public List<ContainerMetricsResponseDTO> collectAllContainerMetrics() {
        return containerMetricsCollector.collectAllContainers();
    }

    public List<DetailedContainerMetricsResponseDTO> collectAllDetailedContainerMetrics() {
        return containerMetricsCollector.collectAllDetailedContainers();
    }

    @Override
    public MetricsWithHostInfoResponseDTO collectMetricsWithHostInfo() {
        // 호스트 메모리 정보 수집
        HostMemoryInfoResponseDTO hostMemory = hostMemoryCollector.collectHostMemory();

        // 컨테이너 상세 메트릭 수집
        List<DetailedContainerMetricsResponseDTO> containers = containerMetricsCollector.collectAllDetailedContainers();

        // 통합 응답 생성
        return MetricsWithHostInfoResponseDTO.builder()
                .host(hostMemory)
                .metrics(containers)
                .build();
    }
}
