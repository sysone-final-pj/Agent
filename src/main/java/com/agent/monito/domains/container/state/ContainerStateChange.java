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
}
