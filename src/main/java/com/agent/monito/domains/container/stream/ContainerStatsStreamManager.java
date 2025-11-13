/**
 * 컨테이너별 Stats 스트림을 백그라운드에서 관리하는 클래스
 *
 * 기능:
 * - 각 컨테이너마다 Stats 스트림을 유지하며 1초마다 자동으로 데이터 수신
 * - 최신 데이터를 메모리 캐시에 저장
 * - 컨테이너 생성/삭제 시 스트림 동적 관리
 * - 연결 끊김 시 자동 재연결
 *
 * 아키텍처:
 * [Docker API] --stream--> [이 클래스] --cache--> [Scheduler가 읽음]
 */
package com.agent.monito.domains.container.stream;

import com.agent.monito.domains.container.cache.InspectContainerCache;
import com.agent.monito.domains.container.dto.collected.DetailedContainerStatsCollectedDTO;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerStatsStreamManager {

    private final DockerClient dockerClient;
    private final InspectContainerCache inspectContainerCache;

    // 컨테이너별 최신 통계 데이터 캐시 (Thread-safe)
    private final Map<String, DetailedContainerStatsCollectedDTO> statsCache = new ConcurrentHashMap<>();

    // 컨테이너별 스트림 연결 관리 (Thread-safe)
    private final Map<String, StreamConnection> activeStreams = new ConcurrentHashMap<>();

    /**
     * 애플리케이션 시작 시 모든 실행 중인 컨테이너의 스트림 시작
     */
    @PostConstruct
    public void initialize() {
        log.info("🚀 ContainerStatsStreamManager 초기화 시작...");
        syncWithRunningContainers();
        log.info("✅ ContainerStatsStreamManager 초기화 완료. {}개 스트림 활성화됨", activeStreams.size());
    }

    /**
     * 애플리케이션 종료 시 모든 스트림 정리
     */
    @PreDestroy
    public void cleanup() {
        log.info("🛑 ContainerStatsStreamManager 종료 중...");
        stopAllStreams();
        log.info("✅ 모든 스트림 종료 완료");
    }

    /**
     * 실행 중인 모든 컨테이너와 스트림 동기화
     * - 새로 생성된 컨테이너: 스트림 시작
     * - 삭제된 컨테이너: 스트림 중지
     */
    public void syncWithRunningContainers() {
        try {
            // 현재 실행 중인 모든 컨테이너 조회 (Agent 제외)
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowSize(true)
                    .exec()
                    .stream()
                    .filter(container -> {
                        String containerName = container.getNames()[0].replace("/", "");
                        return !containerName.equals("agent-monito");
                    })
                    .collect(Collectors.toList());

            Set<String> currentContainerIds = containers.stream()
                    .map(Container::getId)
                    .collect(Collectors.toSet());

            // 1. 새로 생성된 컨테이너: 스트림 시작
            for (Container container : containers) {
                String containerId = container.getId();
                if (!activeStreams.containsKey(containerId)) {
                    String containerName = container.getNames()[0].replace("/", "");
                    String status = cleanStatus(container.getStatus());
                    String state = container.getState();
                    Long sizeRw = container.getSizeRw();
                    Long sizeRootFs = container.getSizeRootFs();

                    log.info("➕ 새 컨테이너 감지: {} ({})", containerName, containerId.substring(0, 12));
                    startStream(containerId, containerName, status, state, sizeRw, sizeRootFs);
                }
            }

            // 2. 삭제된 컨테이너: 스트림 중지
            Set<String> streamsToRemove = activeStreams.keySet().stream()
                    .filter(id -> !currentContainerIds.contains(id))
                    .collect(Collectors.toSet());

            for (String containerId : streamsToRemove) {
                log.info("➖ 컨테이너 삭제 감지: {}", containerId.substring(0, 12));
                stopStream(containerId);
            }

            log.debug("📊 스트림 동기화 완료: 활성 스트림 {}개, 캐시 데이터 {}개",
                    activeStreams.size(), statsCache.size());

        } catch (Exception e) {
            log.error("❌ 컨테이너 동기화 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 특정 컨테이너의 스트림 시작
     */
    private void startStream(String containerId, String containerName, String status,
                             String state, Long sizeRw, Long sizeRootFs) {
        try {
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            StreamConnection connection = new StreamConnection(containerId, containerName);

            statsCmd.exec(new ResultCallback.Adapter<Statistics>() {
                @Override
                public void onNext(Statistics stats) {
                    // Statistics 유효성 검증
                    if (stats == null || stats.getCpuStats() == null || stats.getCpuStats().getCpuUsage() == null) {
                        log.warn("⚠️ 유효하지 않은 Stats 수신: {} - Stats가 null이거나 incomplete", containerName);
                        return;
                    }

                    // Health 정보 조회 (InspectCache 사용 - TTL 30초)
                    String healthStatus = inspectContainerCache.getHealthStatus(containerId);

                    // 최신 데이터를 캐시에 저장
                    DetailedContainerStatsCollectedDTO data = DetailedContainerStatsCollectedDTO.builder()
                            .containerHash(containerId)
                            .containerName(containerName)
                            .status(status)
                            .state(state)
                            .health(healthStatus)
                            .sizeRw(sizeRw)
                            .sizeRootFs(sizeRootFs)
                            .statistics(stats)
                            .build();

                    statsCache.put(containerId, data);
                    connection.updateLastReceived();
                    log.trace("📊 Stats 수신: {} (캐시 크기: {})", containerName, statsCache.size());
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("❌ 스트림 에러 발생: {} - {}", containerName, throwable.getMessage());
                    log.info("→ 다음 동기화 주기에 자동 재연결 시도 (컨테이너가 실행 중인 경우)");
                    connection.markAsErrored();

                    // 에러 발생한 스트림 정리
                    // syncWithRunningContainers()가 5초마다 실행되면서
                    // 컨테이너가 실행 중이면 자동으로 재연결됨
                    stopStream(containerId);
                }

                @Override
                public void onComplete() {
                    log.debug("✅ 스트림 완료: {}", containerName);
                    connection.markAsCompleted();
                }
            });

            connection.setStatsCmd(statsCmd);
            activeStreams.put(containerId, connection);
            log.info("🔄 스트림 시작됨: {} ({})", containerName, containerId.substring(0, 12));

        } catch (Exception e) {
            log.error("❌ 스트림 시작 실패: {} - {}", containerName, e.getMessage(), e);
        }
    }

    /**
     * 특정 컨테이너의 스트림 중지
     */
    private void stopStream(String containerId) {
        StreamConnection connection = activeStreams.remove(containerId);
        if (connection != null) {
            try {
                connection.close();
                statsCache.remove(containerId);
                log.debug("🛑 스트림 중지됨: {}", containerId.substring(0, 12));
            } catch (Exception e) {
                log.warn("⚠️ 스트림 종료 중 에러: {}", e.getMessage());
            }
        }
    }

    /**
     * 모든 스트림 중지
     */
    private void stopAllStreams() {
        activeStreams.keySet().forEach(this::stopStream);
    }

    /**
     * 모든 컨테이너의 최신 통계 데이터 조회
     */
    public List<DetailedContainerStatsCollectedDTO> getAllLatestStats() {
        return new ArrayList<>(statsCache.values());
    }

    /**
     * 특정 컨테이너의 최신 통계 데이터 조회
     */
    public DetailedContainerStatsCollectedDTO getLatestStats(String containerId) {
        return statsCache.get(containerId);
    }

    /**
     * 현재 활성화된 스트림 개수
     */
    public int getActiveStreamCount() {
        return activeStreams.size();
    }

    /**
     * 캐시된 데이터 개수
     */
    public int getCacheSize() {
        return statsCache.size();
    }

    /**
     * status 문자열에서 괄호로 둘러싸인 health 정보 제거
     */
    private String cleanStatus(String status) {
        if (status == null) {
            return null;
        }
        return status.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
    }

    /**
     * 스트림 연결 정보를 관리하는 내부 클래스
     */
    private static class StreamConnection implements Closeable {
        private final String containerId;
        private final String containerName;
        private StatsCmd statsCmd;
        private long lastReceivedTime;
        private boolean errored;
        private boolean completed;

        public StreamConnection(String containerId, String containerName) {
            this.containerId = containerId;
            this.containerName = containerName;
            this.lastReceivedTime = System.currentTimeMillis();
            this.errored = false;
            this.completed = false;
        }

        public void setStatsCmd(StatsCmd statsCmd) {
            this.statsCmd = statsCmd;
        }

        public void updateLastReceived() {
            this.lastReceivedTime = System.currentTimeMillis();
        }

        public void markAsErrored() {
            this.errored = true;
        }

        public void markAsCompleted() {
            this.completed = true;
        }

        @Override
        public void close() {
            if (statsCmd != null) {
                try {
                    statsCmd.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}