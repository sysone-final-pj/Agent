/**
 * CPU 관련 메트릭 데이터 (원시 데이터만 포함)
 */
package com.agent.monito.domains.container.dto.response;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CpuMetricsResponseDTO {
    private Long cpuUsageTotal;             // 총 CPU 사용량 (nanoseconds)
    private Long cpuUser;                   // User mode CPU 사용량 (nanoseconds)
    private Long cpuSystem;                 // System mode CPU 사용량 (nanoseconds)
    private Long systemCpuUsage;            // 시스템 전체 CPU 사용량 (nanoseconds)
    private Long onlineCpus;                // 온라인 CPU 개수
    private Long cpuQuota;                  // CPU quota (cgroup) - from inspect API
    private Long cpuPeriod;                 // CPU period (cgroup) - from inspect API
    private Long throttlingPeriods;         // Throttling이 활성화된 기간 수
    private Long throttledPeriods;          // Throttled된 기간 수
    private Long throttledTime;             // Throttled된 총 시간 (nanoseconds)
}