/**
 * Memory 관련 메트릭 데이터 (원시 데이터만 포함)
 */
package com.agent.monito.domains.container.dto.response;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemoryMetricsResponseDTO {
    private Long memUsage;                  // 현재 메모리 사용량 (bytes)
    private Long memLimit;                  // 메모리 제한 (bytes)
    private Long memMaxUsage;               // 최대 메모리 사용량 (bytes)
}