/**
 * 호스트 정보와 컨테이너 메트릭을 함께 담는 통합 응답 DTO
 * JSON 구조:
 * {
 *   "host": { "totalMemory": ..., "availableMemory": ..., "usedMemory": ... },
 *   "metrics": [ { ... }, { ... } ]
 * }
 */
package com.agent.monito.domains.container.dto.response;

import com.agent.monito.domains.agent.dto.response.HostMemoryInfoResponseDTO;
import lombok.*;

import java.util.List;
/**
 작성자: 백승준
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MetricsWithHostInfoResponseDTO {
    private HostMemoryInfoResponseDTO host;
    private List<DetailedContainerMetricsResponseDTO> metrics;
}
