/**
 * Docker API를 통해 실행 중인 컨테이너의 원시 Statistics 데이터를 수집하는 클래스
 * DTO 변환이나 계산 로직 없이 순수하게 데이터 수집만 담당
 */
package com.agent.monito.domains.container.collector;

import com.agent.monito.domains.container.cache.InspectContainerCache;
import com.agent.monito.domains.container.state.ContainerSnapshot;
import com.agent.monito.global.util.ContainerFilterUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.Container;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class ContainerSnapshotCollector {

    private final DockerClient dockerClient;
    private final InspectContainerCache inspectContainerCache;

    /**
     * 컨테이너의 OOMKilled 상태 조회 (캐시 적용)
     *
     * @param containerHash 컨테이너 ID
     * @return OOMKilled 여부
     */
    private Boolean getOOMKilledStatus(String containerHash) {
        try {
            InspectContainerResponse inspectResponse = inspectContainerCache.getOrFetch(containerHash);
            InspectContainerResponse.ContainerState state = inspectResponse.getState();

            if (state != null) {
                Boolean oomKilled = state.getOOMKilled();
                return oomKilled != null ? oomKilled : false;
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to get OOMKilled status for container {}: {}", containerHash, e.getMessage());
            return false;
        }
    }

    /**
     * status 문자열에서 괄호로 둘러싸인 health 정보 제거
     * 예: "Up 27 seconds (healthy)" -> "Up 27 seconds"
     *
     * @param status 원본 status 문자열
     * @return 정제된 status 문자열
     */
    private String cleanStatus(String status) {
        if (status == null) {
            return null;
        }
        return status.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
    }

    /**
     * 모든 컨테이너의 상태 스냅샷 수집 (메트릭 없이, 상태 동기화용)
     * 실행 중인 컨테이너 + 종료된 컨테이너 모두 포함
     *
     * @return 컨테이너 스냅샷 리스트
     */
    public List<ContainerSnapshot> collectAllContainerSnapshots() {
        try {
            // 모든 컨테이너 조회 (실행 중 + 종료됨)
            List<Container> allContainers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .exec();

            // Agent 자신의 컨테이너 제외
            allContainers = allContainers.stream()
                    .filter(ContainerFilterUtil::isNotAgentContainer)
                    .collect(Collectors.toList());

            List<ContainerSnapshot> snapshots = new ArrayList<>();
            for (Container container : allContainers) {
                String containerHash = container.getId();
                String containerName = container.getNames()[0].replace("/", "");
                String state = container.getState();
                String status = container.getStatus();
                String imageName = container.getImage();
                String imageId = container.getImageId();
                Long imageSize = getImageSize(imageId);

                // OOMKilled 정보 수집
                Boolean oomKilled = getOOMKilledStatus(containerHash);

                snapshots.add(ContainerSnapshot.builder()
                        .containerHash(containerHash)
                        .containerName(containerName)
                        .state(state)
                        .status(status)
                        .imageName(imageName)
                        .imageId(imageId)
                        .imageSize(imageSize)
                        .oomKilled(oomKilled)
                        .build());
            }

            log.info("Collected {} container snapshots (running + stopped)", snapshots.size());
            return snapshots;

        } catch (Exception e) {
            log.error("Error collecting container snapshots", e);
            return new ArrayList<>();
        }
    }

    /**
     * 이미지 크기 조회
     *
     * @param imageId 이미지 ID
     * @return 이미지 크기 (bytes), 실패 시 0L
     */
    private Long getImageSize(String imageId) {
        if (imageId == null || imageId.isEmpty()) {
            return 0L;
        }

        try {
            InspectImageResponse imageInfo =
                    dockerClient.inspectImageCmd(imageId).exec();
            Long size = imageInfo.getSize();
            return size != null ? size : 0L;
        } catch (Exception e) {
            log.debug("Failed to get image size for imageId {}: {}", imageId, e.getMessage());
            return 0L;
        }
    }
}