/**
 * Memory 관련 메트릭 데이터 (원시 데이터만 포함)
 */
package com.agent.monito.domains.container.dto.response;

import lombok.*;
/**
 작성자: 백승준
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemoryMetricsResponseDTO {
    private Long memUsage;                  // 현재 메모리 사용량 (bytes)
    private Long memLimit;                  // 메모리 제한 (bytes)
    private Long memMaxUsage;               // 최대 메모리 사용량 (bytes) - Cgroup v2에서는 0
    private Boolean isMemoryUnlimited;      // 메모리 제한 없음 여부 (true: 무제한, false: 제한 있음)
}