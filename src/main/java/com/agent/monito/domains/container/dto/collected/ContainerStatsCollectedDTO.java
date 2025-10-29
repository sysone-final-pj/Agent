/**
 * 컨테이너의 원시 통계 데이터 (Collector에서 수집한 데이터)
 */
package com.agent.monito.domains.container.dto.collected;

import com.github.dockerjava.api.model.Statistics;
import lombok.*;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContainerStatsCollectedDTO {
    private String containerHash;
    private String containerName;
    private Statistics statistics;
}