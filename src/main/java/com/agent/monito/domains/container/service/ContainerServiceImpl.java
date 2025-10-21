package com.agent.monito.domains.container.service;

import com.agent.monito.domains.container.collector.ContainerMetricsCollector;
import com.agent.monito.domains.container.dto.response.ContainerMetricsResponseDTO;
import com.agent.monito.domains.container.dto.response.DetailedContainerMetricsResponseDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContainerServiceImpl implements ContainerService{

    private final ContainerMetricsCollector containerMetricsCollector;

    public List<ContainerMetricsResponseDTO> collectAllContainerMetrics() {
        return containerMetricsCollector.collectAllContainers();
    }

    public List<DetailedContainerMetricsResponseDTO> collectAllDetailedContainerMetrics() {
        return containerMetricsCollector.collectAllDetailedContainers();
    }
}
