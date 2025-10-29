package com.agent.monito.domains.container.service;

import com.agent.monito.domains.agent.collector.HostMemoryCollector;
import com.agent.monito.domains.agent.dto.response.HostMemoryInfoResponseDTO;
import com.agent.monito.domains.container.collector.ContainerMetricsCollector;
import com.agent.monito.domains.container.collector.ContainerLogsCollector;
import com.agent.monito.domains.container.dto.collected.ContainerLogsCollectedDTO;
import com.agent.monito.domains.container.dto.collected.ContainerStatsCollectedDTO;
import com.agent.monito.domains.container.dto.collected.DetailedContainerStatsCollectedDTO;
import com.agent.monito.domains.container.dto.response.ContainerLogEntryResponseDTO;
import com.agent.monito.domains.container.dto.response.ContainerMetricsResponseDTO;
import com.agent.monito.domains.container.dto.response.DetailedContainerMetricsResponseDTO;
import com.agent.monito.domains.container.dto.response.MetricsWithHostInfoResponseDTO;
import com.agent.monito.global.mapper.ContainerLogsMapper;
import com.agent.monito.global.mapper.ContainerMetricsMapper;
import com.github.dockerjava.api.model.Frame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContainerServiceImpl implements ContainerService {

    private final ContainerMetricsCollector containerMetricsCollector;
    private final ContainerLogsCollector containerLogsCollector;
    private final HostMemoryCollector hostMemoryCollector;
    private final ContainerMetricsMapper containerMetricsMapper;
    private final ContainerLogsMapper containerLogsMapper;

    @Override
    public List<ContainerMetricsResponseDTO> collectAllContainerMetrics() {
        // Collector에서 원시 데이터 수집
        List<ContainerStatsCollectedDTO> collectedData = containerMetricsCollector.collectAllContainers();

        // Mapper로 DTO 변환
        return collectedData.stream()
                .map(data -> containerMetricsMapper.toSimpleMetricsDTO(
                        data.getContainerName(),
                        data.getStatistics()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<DetailedContainerMetricsResponseDTO> collectAllDetailedContainerMetrics() {
        // Collector에서 원시 데이터 수집
        List<DetailedContainerStatsCollectedDTO> collectedData =
                containerMetricsCollector.collectAllDetailedContainers();

        // Mapper로 DTO 변환
        return collectedData.stream()
                .map(data -> {
                    if (data.getStatistics() == null) {
                        // Statistics가 null인 경우 빈 메트릭 반환
                        return containerMetricsMapper.buildEmptyDetailedMetrics(
                                data.getContainerHash(),
                                data.getContainerName(),
                                data.getStatus(),
                                data.getSizeRw(),
                                data.getSizeRootFs()
                        );
                    }
                    return containerMetricsMapper.toDetailedMetricsDTO(
                            data.getContainerHash(),
                            data.getContainerName(),
                            data.getStatus(),
                            data.getState(),
                            data.getSizeRw(),
                            data.getSizeRootFs(),
                            data.getStatistics()
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public MetricsWithHostInfoResponseDTO collectMetricsWithHostInfo() {
        // 호스트 메모리 정보 수집
        HostMemoryInfoResponseDTO hostMemory = hostMemoryCollector.collectHostMemory();

        // 컨테이너 상세 메트릭 수집
        List<DetailedContainerMetricsResponseDTO> containers = collectAllDetailedContainerMetrics();

        // 통합 응답 생성
        return MetricsWithHostInfoResponseDTO.builder()
                .host(hostMemory)
                .metrics(containers)
                .build();
    }

    @Override
    public Map<String, List<ContainerLogEntryResponseDTO>> collectAllContainerLogs(int tailLines) {
        return collectAllContainerLogs(tailLines, null);
    }

    @Override
    public Map<String, List<ContainerLogEntryResponseDTO>> collectAllContainerLogs(
            int tailLines,
            Map<String, Integer> containerSinceMap) {
        // Collector에서 원시 로그 데이터 수집
        Map<String, ContainerLogsCollectedDTO> collectedLogs =
                containerLogsCollector.collectAllContainerLogs(tailLines, containerSinceMap);

        // Mapper로 DTO 변환 (containerHash -> 로그 리스트)
        Map<String, List<ContainerLogEntryResponseDTO>> result = new HashMap<>();

        collectedLogs.forEach((containerHash, collectedData) -> {
            List<ContainerLogEntryResponseDTO> logEntries = new ArrayList<>();

            // 각 Frame을 LogEntryResponseDTO로 변환
            for (Frame frame : collectedData.getFrames()) {
                ContainerLogEntryResponseDTO logEntry = containerLogsMapper.toLogEntryDTO(frame);
                if (logEntry != null) {
                    logEntries.add(logEntry);
                }
            }

            result.put(containerHash, logEntries);
        });

        return result;
    }
}