/**
 * Docker API를 통해 실행 중인 컨테이너의 원시 Statistics 데이터를 수집하는 클래스
 * DTO 변환이나 계산 로직 없이 순수하게 데이터 수집만 담당
 */
package com.agent.monito.domains.container.collector;

import com.agent.monito.domains.container.dto.collected.ContainerStatsCollectedDTO;
import com.agent.monito.domains.container.dto.collected.DetailedContainerStatsCollectedDTO;
import com.agent.monito.domains.container.state.ContainerSnapshot;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class ContainerMetricsCollector {

    private final DockerClient dockerClient;

    /**
     * 실행 중인 모든 컨테이너의 간단한 통계 수집
     *
     * @return 컨테이너 통계 데이터 리스트
     */
    public List<ContainerStatsCollectedDTO> collectAllContainers() {
        try {
            List<Container> containers = dockerClient.listContainersCmd().exec();
            log.info("Found {} running containers", containers.size());

            List<CompletableFuture<ContainerStatsCollectedDTO>> futures = containers.stream()
                    .map(container -> CompletableFuture.supplyAsync(() -> {
                        String containerHash = container.getId();
                        String containerName = container.getNames()[0].replace("/", "");
                        log.info("Collecting stats for container: {}", containerName);
                        return collectSingleContainer(containerHash, containerName);
                    }))
                    .collect(Collectors.toList());

            return futures.stream()
                    .map(future -> {
                        try {
                            return future.join();
                        } catch (Exception e) {
                            log.error("Failed to collect container stats", e);
                            return null;
                        }
                    })
                    .filter(data -> data != null)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error collecting container stats", e);
            return new ArrayList<>();
        }
    }

    /**
     * 실행 중인 모든 컨테이너의 상세 통계 수집 (사이즈 정보 포함)
     *
     * @return 상세 컨테이너 통계 데이터 리스트
     */
    public List<DetailedContainerStatsCollectedDTO> collectAllDetailedContainers() {
        long startTime = System.currentTimeMillis();
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowSize(true)
                    .exec();
            log.info("Found {} running containers", containers.size());

            List<CompletableFuture<DetailedContainerStatsCollectedDTO>> futures = containers.stream()
                    .map(container -> CompletableFuture.supplyAsync(() -> {
                        String containerHash = container.getId();
                        String containerName = container.getNames()[0].replace("/", "");
                        String status = cleanStatus(container.getStatus());
                        String state = container.getState();
                        String health = getHealthStatus(containerHash);
                        Long sizeRw = container.getSizeRw();
                        Long sizeRootFs = container.getSizeRootFs();
                        log.info("Collecting detailed stats for container: {}", containerName);
                        return collectSingleDetailedContainer(containerHash, containerName, status, state, health, sizeRw, sizeRootFs);
                    }))
                    .collect(Collectors.toList());

            List<DetailedContainerStatsCollectedDTO> results = futures.stream()
                    .map(future -> {
                        try {
                            return future.join();
                        } catch (Exception e) {
                            log.error("Failed to collect detailed container stats", e);
                            return null;
                        }
                    })
                    .filter(data -> data != null)
                    .collect(Collectors.toList());

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("✓ Collected stats for {} containers in {}ms", results.size(), totalTime);
            return results;

        } catch (Exception e) {
            log.error("Error collecting detailed container stats", e);
            return new ArrayList<>();
        }
    }

    /**
     * 단일 컨테이너의 통계 수집
     */
    private ContainerStatsCollectedDTO collectSingleContainer(String containerHash, String containerName) {
        try (StatsCmd statsCmd = dockerClient.statsCmd(containerHash).withNoStream(true)) {
            final CountDownLatch latch = new CountDownLatch(1);
            final ContainerStatsCollectedDTO[] result = new ContainerStatsCollectedDTO[1];

            statsCmd.exec(new ResultCallback.Adapter<Statistics>() {
                @Override
                public void onNext(Statistics stats) {
                    result[0] = ContainerStatsCollectedDTO.builder()
                            .containerHash(containerHash)
                            .containerName(containerName)
                            .statistics(stats)
                            .build();
                    latch.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("Error while collecting stats for {}", containerName, throwable);
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    log.debug("Stats collection completed for {}", containerName);
                }
            });

            latch.await(2, TimeUnit.SECONDS);
            return result[0];

        } catch (Exception e) {
            log.error("Failed to collect stats for {}", containerName, e);
            return null;
        }
    }

    /**
     * 단일 컨테이너의 상세 통계 수집
     */
    private DetailedContainerStatsCollectedDTO collectSingleDetailedContainer(
            String containerHash,
            String containerName,
            String status,
            String state,
            String health,
            Long sizeRw,
            Long sizeRootFs) {

        long startTime = System.currentTimeMillis();
        try (StatsCmd statsCmd = dockerClient.statsCmd(containerHash).withNoStream(true)) {
            final CountDownLatch latch = new CountDownLatch(1);
            final DetailedContainerStatsCollectedDTO[] result = new DetailedContainerStatsCollectedDTO[1];

            statsCmd.exec(new ResultCallback.Adapter<Statistics>() {
                @Override
                public void onNext(Statistics stats) {
                    log.info("📊 Received stats for container: {}", containerName);
                    result[0] = DetailedContainerStatsCollectedDTO.builder()
                            .containerHash(containerHash)
                            .containerName(containerName)
                            .status(status)
                            .state(state)
                            .health(health)
                            .sizeRw(sizeRw)
                            .sizeRootFs(sizeRootFs)
                            .statistics(stats)
                            .build();
                    latch.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("❌ Error while collecting detailed stats for {}: {}", containerName, throwable.getMessage(), throwable);
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    log.debug("Detailed stats collection completed for {}", containerName);
                }
            });

            boolean completed = latch.await(3, TimeUnit.SECONDS);
            long elapsedTime = System.currentTimeMillis() - startTime;

            if (!completed) {
                log.warn("⏱️ Timeout waiting for stats from container: {} after {}ms", containerName, elapsedTime);
            } else {
                log.debug("✓ Stats collected for {} in {}ms", containerName, elapsedTime);
            }

            if (result[0] == null) {
                log.warn("⚠️ No stats received for container: {} ({})", containerName, containerHash);
            }
            return result[0];

        } catch (Exception e) {
            log.error("Failed to collect detailed stats for {}", containerName, e);
            return null;
        }
    }

    /**
     * 컨테이너의 health status 조회
     *
     * @param containerHash 컨테이너 ID
     * @return health status (healthy, unhealthy, starting, none, unknown)
     */
    private String getHealthStatus(String containerHash) {
        try {
            InspectContainerResponse inspectResponse = dockerClient.inspectContainerCmd(containerHash).exec();
            InspectContainerResponse.ContainerState state = inspectResponse.getState();

            if (state != null && state.getHealth() != null) {
                String healthStatus = state.getHealth().getStatus();
                return healthStatus != null ? healthStatus : "none";
            }
            return "none";
        } catch (Exception e) {
            log.warn("Failed to get health status for container {}: {}", containerHash, e.getMessage());
            return "unknown";
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

            List<ContainerSnapshot> snapshots = new ArrayList<>();
            for (Container container : allContainers) {
                String containerHash = container.getId();
                String containerName = container.getNames()[0].replace("/", "");
                String state = container.getState();
                String imageName = container.getImage();
                Long imageSize = getImageSize(container.getImageId());

                snapshots.add(ContainerSnapshot.builder()
                        .containerHash(containerHash)
                        .containerName(containerName)
                        .state(state)
                        .imageName(imageName)
                        .imageSize(imageSize)
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
            com.github.dockerjava.api.command.InspectImageResponse imageInfo =
                    dockerClient.inspectImageCmd(imageId).exec();
            Long size = imageInfo.getSize();
            return size != null ? size : 0L;
        } catch (Exception e) {
            log.debug("Failed to get image size for imageId {}: {}", imageId, e.getMessage());
            return 0L;
        }
    }
}