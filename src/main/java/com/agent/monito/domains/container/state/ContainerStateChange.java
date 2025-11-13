/**
 * 컨테이너 스냅샷 (특정 시점의 컨테이너 상태)
 */
package com.agent.monito.domains.container.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ContainerStateChange {
    private String containerHash;
    private String containerName;
    private String oldState;
    private String newState;
    private String oldStatus;       // 이전 상태 상세 정보
    private String newStatus;       // 새로운 상태 상세 정보
    private Boolean oomKilled;      // OOM으로 종료되었는지 여부
}
