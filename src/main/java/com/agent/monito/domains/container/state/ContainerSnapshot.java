/**
 * 컨테이너 스냅샷 (특정 시점의 컨테이너 상태)
 */
package com.agent.monito.domains.container.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
/**
 작성자: 백승준
 */
@Getter
@Builder
@AllArgsConstructor
public class ContainerSnapshot {
    private String containerHash;
    private String containerName;
    private String state;      // 컨테이너 상태 (running, exited, created 등)
    private String status;     // 상태 상세 정보 (예: "Up 2 hours", "Exited (0) 47 hours ago")
    private String imageName;  // 컨테이너 이미지 이름 (예: nginx:latest)
    private String imageId;    // 이미지 ID (예: f3573d4a0cf2)
    private Long imageSize;    // 이미지 크기 - bytes
    private Boolean oomKilled; // OOM으로 종료되었는지 여부
}
