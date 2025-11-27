/**
 * 상태 변화 감지 결과
 */
package com.agent.monito.domains.container.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
/**
 작성자: 백승준
 */
@Getter
@Builder
@AllArgsConstructor
public class StateChangeResult {
    private List<ContainerSnapshot> newContainers;         // 새로 생성된 컨테이너
    private List<ContainerSnapshot> stoppedContainers;     // 종료된 컨테이너
    private List<ContainerStateChange> stateChanges;       // 상태가 변경된 컨테이너
}
