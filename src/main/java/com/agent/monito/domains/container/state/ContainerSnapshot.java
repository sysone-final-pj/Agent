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
public class ContainerSnapshot {
    private String containerHash;
    private String containerName;
    private String state;
    private String imageName;  // 컨테이너 이미지 이름 (예: nginx:latest)
    private Long imageSize;    // 이미지 크기 - bytes
    private Boolean oomKilled; // OOM으로 종료되었는지 여부
}
