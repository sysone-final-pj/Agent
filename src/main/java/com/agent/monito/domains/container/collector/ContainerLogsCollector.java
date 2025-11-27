/**
 * Docker API를 통해 실행 중인 컨테이너의 원시 로그 Frame 데이터를 수집하는 클래스
 * DTO 변환이나 파싱 로직 없이 순수하게 데이터 수집만 담당
 */
package com.agent.monito.domains.container.collector;

import com.agent.monito.domains.container.dto.collected.ContainerLogsCollectedDTO;
import com.agent.monito.global.util.ContainerFilterUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
/**
 작성자: 백승준
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ContainerLogsCollector {

    private final DockerClient dockerClient;

    /**
     * 모든 실행 중인 컨테이너의 로그 수집 (tailLines 방식)
     *
     * @param tailLines 각 컨테이너에서 가져올 최근 로그 라인 수
     * @return 컨테이너별 로그 데이터 맵 (containerHash -> ContainerLogsCollectedDTO)
     */
    public Map<String, ContainerLogsCollectedDTO> collectAllContainerLogs(int tailLines) {
        return collectAllContainerLogs(tailLines, null);
    }

    /**
     * 모든 실행 중인 컨테이너의 로그 수집 (since 방식)
     * 특정 시점 이후의 로그만 수집하여 중복 방지
     *
     * @param tailLines 첫 수집 시 가져올 최근 로그 라인 수 (since가 없는 컨테이너용)
     * @param containerSinceMap 컨테이너별 마지막 수집 시점 (Unix timestamp - 초 단위)
     * @return 컨테이너별 로그 데이터 맵 (containerHash -> ContainerLogsCollectedDTO)
     */
    public Map<String, ContainerLogsCollectedDTO> collectAllContainerLogs(
            int tailLines,
            Map<String, Integer> containerSinceMap) {
        Map<String, ContainerLogsCollectedDTO> allLogs = new HashMap<>();

        try {
            // Agent 컨테이너 제외하고 조회
            List<Container> containers = dockerClient.listContainersCmd().exec()
                    .stream()
                    .filter(ContainerFilterUtil::isNotAgentContainer)
                    .collect(Collectors.toList());

            log.info("Found {} running containers for log collection (excluded agent)", containers.size());

            for (Container container : containers) {
                String containerHash = container.getId();
                String containerName = container.getNames()[0].replace("/", "");

                // 해당 컨테이너의 마지막 수집 시점 확인
                Integer since = (containerSinceMap != null) ? containerSinceMap.get(containerHash) : null;

                log.info("Collecting logs from container: {} ({}) since={}",
                        containerName, containerHash, since);

                ContainerLogsCollectedDTO logs = collectSingleContainerLogs(
                        containerHash, containerName, tailLines, since);

                if (logs != null) {
                    allLogs.put(containerHash, logs);
                }
            }

        } catch (Exception e) {
            log.error("Error collecting container logs", e);
        }

        return allLogs;
    }

    /**
     * 특정 컨테이너의 로그 수집 (원시 Frame 데이터)
     *
     * @param containerHash 컨테이너 ID
     * @param containerName 컨테이너 이름 (로깅용)
     * @param tailLines     가져올 최근 로그 라인 수 (since가 null일 때 사용)
     * @param since         Unix timestamp (초 단위) - 이 시점 이후의 로그만 수집
     * @return ContainerLogsCollectedDTO (Frame 리스트 포함)
     */
    private ContainerLogsCollectedDTO collectSingleContainerLogs(
            String containerHash,
            String containerName,
            int tailLines,
            Integer since) {
        List<Frame> frames = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            LogContainerCmd logCmd = dockerClient.logContainerCmd(containerHash)
                    .withStdOut(true)      // stdout 포함
                    .withStdErr(true)      // stderr 포함
                    .withTimestamps(true); // 타임스탬프 포함

            // since가 있으면 특정 시점 이후의 로그만, 없으면 최근 N개 라인
            if (since != null) {
                logCmd.withSince(since);
                log.debug("Using since mode: {} for container {}", since, containerName);
            } else {
                logCmd.withTail(tailLines);
                log.debug("Using tail mode: {} lines for container {}", tailLines, containerName);
            }

            logCmd.exec(new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    frames.add(frame);
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("Error while collecting logs for {}: {}", containerName, throwable.getMessage());
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    log.debug("Log collection completed for {}", containerName);
                    latch.countDown();
                }
            });

            // 로그 수집 대기 (최대 5초)
            boolean completed = latch.await(5, TimeUnit.SECONDS);

            if (!completed) {
                log.warn("Timeout waiting for logs from container: {}", containerName);
            } else {
                log.info("Successfully collected {} log frames from {} (since={})",
                        frames.size(), containerName, since);
            }

            return ContainerLogsCollectedDTO.builder()
                    .containerHash(containerHash)
                    .containerName(containerName)
                    .frames(frames)
                    .build();

        } catch (Exception e) {
            log.error("Failed to collect logs for container {}: {}", containerName, e.getMessage(), e);
            return null;
        }
    }
}