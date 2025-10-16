/**
 * 컨트롤러와 DockerMetricsCollector 사이의 중간 서비스 계층
 * 수집된 메트릭 데이터를 가공하거나 필터링하여 컨트롤러로 전달함.
 */
package com.agent.monito.service;

import com.agent.monito.collector.DockerMetricsCollector;
import com.agent.monito.dto.response.MetricsResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final DockerMetricsCollector dockerMetricsCollector;

    public List<MetricsResponseDTO> collectAllContainerMetrics() {
        return dockerMetricsCollector.collectAllContainers();
    }
}