/**
 * 컨테이너의 모든 세부 메트릭 데이터를 담는 상세 응답 객체
 * CPU, Memory, Network, Block I/O의 모든 세부 필드를 포함
 */
package com.agent.monito.domains.container.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DetailedContainerMetricsResponseDTO {

    // 기본 정보
    private String containerHash;
    private String containerName;
    private String status;
    private String state;

    // 메트릭 수집 시간 (Agent에서 실제로 수집한 시간)
    private LocalDateTime collectedAt;

    // CPU 관련 메트릭
    private CpuMetricsResponseDTO cpu;

    // Memory 관련 메트릭
    private MemoryMetricsResponseDTO memory;

    // Network 관련 메트릭
    private NetworkMetricsResponseDTO network;

    // Block I/O 관련 메트릭
    private BlockIOMetricsResponseDTO blockIO;

    // Storage 관련 메트릭
    private StorageMetricsResponseDTO storage;
}