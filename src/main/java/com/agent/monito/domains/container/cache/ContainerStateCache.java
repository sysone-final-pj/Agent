/**
 * 컨테이너별 상태 정보를 관리하는 캐시
 * 상태 변화 감지를 위해 이전 수집 결과를 저장
 */
package com.agent.monito.domains.container.cache;

import com.agent.monito.domains.container.state.ContainerSnapshot;
import com.agent.monito.domains.container.state.ContainerStateChange;
import com.agent.monito.domains.container.state.StateChangeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ContainerStateCache {

    /**
     * 컨테이너별 상태 정보 캐시
     * Key: containerHash, Value: ContainerSnapshot
     */
    private final Map<String, ContainerSnapshot> containerStates = new ConcurrentHashMap<>();

    /**
     * 특정 컨테이너의 이전 상태 조회
     *
     * @param containerHash 컨테이너 ID
     * @return ContainerSnapshot, 없으면 null
     */
    public ContainerSnapshot getState(String containerHash) {
        return containerStates.get(containerHash);
    }

    /**
     * 모든 컨테이너의 상태 조회
     *
     * @return containerHash -> ContainerSnapshot 맵
     */
    public Map<String, ContainerSnapshot> getAllStates() {
        return new HashMap<>(containerStates);
    }

    /**
     * 컨테이너 상태 업데이트
     *
     * @param containerHash 컨테이너 ID
     * @param containerName 컨테이너 이름
     * @param state 컨테이너 상태 (running, exited, created, etc.)
     */
    public void updateState(String containerHash, String containerName, String state) {
        ContainerSnapshot snapshot = ContainerSnapshot.builder()
                .containerHash(containerHash)
                .containerName(containerName)
                .state(state)
                .build();

        containerStates.put(containerHash, snapshot);
        log.debug("Updated state for container {}: {}", containerName, state);
    }

    /**
     * 여러 컨테이너의 상태를 일괄 업데이트
     *
     * @param snapshots 컨테이너 스냅샷 리스트
     */
    public void updateStates(List<ContainerSnapshot> snapshots) {
        for (ContainerSnapshot snapshot : snapshots) {
            containerStates.put(snapshot.getContainerHash(), snapshot);
        }
        log.info("Bulk updated {} container states", snapshots.size());
    }

    /**
     * 상태 변화 감지
     * 이전 상태와 현재 상태를 비교하여 변화를 감지
     *
     * @param currentContainers 현재 컨테이너 스냅샷 리스트
     * @return 상태 변화 결과 (새로 생성, 종료됨, 상태 변경)
     */
    public StateChangeResult detectChanges(List<ContainerSnapshot> currentContainers) {
        Map<String, ContainerSnapshot> currentMap = new HashMap<>();
        for (ContainerSnapshot snapshot : currentContainers) {
            currentMap.put(snapshot.getContainerHash(), snapshot);
        }

        List<ContainerSnapshot> newContainers = new ArrayList<>();
        List<ContainerSnapshot> stoppedContainers = new ArrayList<>();
        List<ContainerStateChange> stateChanges = new ArrayList<>();

        // 1. 새로 생성된 컨테이너 찾기
        for (ContainerSnapshot current : currentContainers) {
            if (!containerStates.containsKey(current.getContainerHash())) {
                newContainers.add(current);
                log.info("New container detected: {} ({})", current.getContainerName(), current.getState());
            } else {
                // 2. 상태가 변경된 컨테이너 찾기
                ContainerSnapshot previous = containerStates.get(current.getContainerHash());
                if (!previous.getState().equals(current.getState())) {
                    stateChanges.add(ContainerStateChange.builder()
                            .containerHash(current.getContainerHash())
                            .containerName(current.getContainerName())
                            .oldState(previous.getState())
                            .newState(current.getState())
                            .build());
                    log.info("State changed: {} ({} -> {})",
                            current.getContainerName(), previous.getState(), current.getState());
                }
            }
        }

        // 3. 종료된 컨테이너 찾기 (docker rm으로 완전히 삭제된 경우)
        for (Map.Entry<String, ContainerSnapshot> entry : containerStates.entrySet()) {
            String hash = entry.getKey();
            if (!currentMap.containsKey(hash)) {
                ContainerSnapshot stopped = entry.getValue();
                stoppedContainers.add(ContainerSnapshot.builder()
                        .containerHash(stopped.getContainerHash())
                        .containerName(stopped.getContainerName())
                        .state("deleted")  // docker rm으로 삭제됨
                        .imageName(stopped.getImageName())
                        .imageSize(stopped.getImageSize())
                        .build());
                log.info("Container deleted: {} (was {})", stopped.getContainerName(), stopped.getState());
            }
        }

        return StateChangeResult.builder()
                .newContainers(newContainers)
                .stoppedContainers(stoppedContainers)
                .stateChanges(stateChanges)
                .build();
    }

    /**
     * 특정 컨테이너의 상태 제거
     *
     * @param containerHash 컨테이너 ID
     */
    public void remove(String containerHash) {
        ContainerSnapshot removed = containerStates.remove(containerHash);
        if (removed != null) {
            log.debug("Removed state for container {}: {}", removed.getContainerName(), removed.getState());
        }
    }

    /**
     * 전체 캐시 초기화
     */
    public void clear() {
        log.info("Clearing all container states");
        containerStates.clear();
    }

    /**
     * 캐시 크기 조회
     *
     * @return 캐시된 컨테이너 수
     */
    public int size() {
        return containerStates.size();
    }
}