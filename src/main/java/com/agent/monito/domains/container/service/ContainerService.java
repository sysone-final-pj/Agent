/**
 * 컨트롤러와 DockerMetricsCollector 사이의 중간 서비스 계층
 * 수집된 메트릭 데이터를 가공하거나 필터링하여 컨트롤러로 전달함.
 */
package com.agent.monito.domains.container.service;

import com.agent.monito.domains.container.collector.ContainerMetricsCollector;
import com.agent.monito.domains.container.dto.response.ContainerMetricsResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContainerService {

    private final ContainerMetricsCollector containerMetricsCollector;

    public List<ContainerMetricsResponseDTO> collectAllContainerMetrics() {
        return containerMetricsCollector.collectAllContainers();
    }
}