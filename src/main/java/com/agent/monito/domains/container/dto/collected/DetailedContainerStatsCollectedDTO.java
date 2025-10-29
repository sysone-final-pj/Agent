/**
 * 컨테이너의 상세 원시 통계 데이터 (Collector에서 수집한 데이터)
 */
package com.agent.monito.domains.container.dto.collected;

import com.github.dockerjava.api.model.Statistics;
import lombok.*;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DetailedContainerStatsCollectedDTO {
    private String containerHash;
    private String containerName;
    private String status;
    private String state;
    private Long sizeRw;
    private Long sizeRootFs;
    private Statistics statistics;
}