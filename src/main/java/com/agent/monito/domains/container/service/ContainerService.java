/**
 * 컨트롤러와 DockerMetricsCollector 사이의 중간 서비스 계층
 * 수집된 메트릭 데이터를 가공하거나 필터링하여 컨트롤러로 전달함.
 */
package com.agent.monito.domains.container.service;

import com.agent.monito.domains.container.dto.response.ContainerLogEntryResponseDTO;
import com.agent.monito.domains.container.dto.response.ContainerMetricsResponseDTO;
import com.agent.monito.domains.container.dto.response.DetailedContainerMetricsResponseDTO;
import com.agent.monito.domains.container.dto.response.MetricsWithHostInfoResponseDTO;

import java.util.List;
import java.util.Map;

public interface ContainerService {

    List<ContainerMetricsResponseDTO> collectAllContainerMetrics();
    List<DetailedContainerMetricsResponseDTO> collectAllDetailedContainerMetrics();
    MetricsWithHostInfoResponseDTO collectMetricsWithHostInfo();
    Map<String, List<ContainerLogEntryResponseDTO>> collectAllContainerLogs(int tailLines);
    Map<String, List<ContainerLogEntryResponseDTO>> collectAllContainerLogs(int tailLines, Map<String, Integer> containerSinceMap);
}