/**
 * 컨테이너의 원시 로그 데이터 (Collector에서 수집한 데이터)
 */
package com.agent.monito.domains.container.dto.collected;

import com.github.dockerjava.api.model.Frame;
import lombok.*;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContainerLogsCollectedDTO {
    private String containerHash;
    private String containerName;
    private List<Frame> frames;  // 원시 Frame 데이터 리스트
}