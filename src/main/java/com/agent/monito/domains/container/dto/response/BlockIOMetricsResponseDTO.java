/**
 * Block I/O 관련 메트릭 데이터 (원시 데이터만 포함)
 */
package com.agent.monito.domains.container.dto.response;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockIOMetricsResponseDTO {
    private Long blkRead;
    private Long blkWrite;
}