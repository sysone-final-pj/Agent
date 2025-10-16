/**
 * Docker API를 통해 실행 중인 모든 컨테이너의 CPU, 메모리, 네트워크, 디스크 I/O 등의 메트릭을 수집하는 클래스
 * 각 컨테이너별 실시간 통계를 단발성으로 가져와 DTO 형태로 반환함.
 */
package com.agent.monito.collector;

import com.agent.monito.dto.response.MetricsResponseDTO;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StatisticNetworksConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DockerMetricsCollector {

    private final DockerClient dockerClient;

    public DockerMetricsCollector(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    // 실행 중인 모든 컨테이너의 메트릭을 수집
    public List<MetricsResponseDTO> collectAllContainers() {
        List<MetricsResponseDTO> responses = new ArrayList<>();

        try {
            List<Container> containers = dockerClient.listContainersCmd().exec();
            log.info("Found {} running containers", containers.size());

            for (Container container : containers) {
                String containerId = container.getId();
                String containerName = container.getNames()[0].replace("/", "");
                log.info("Collecting stats for container: {}", containerName);

                MetricsResponseDTO metrics = collectSingleContainer(containerId, containerName);
                responses.add(metrics);
            }

        } catch (Exception e) {
            log.error("Error collecting container stats", e);
        }

        return responses;
    }

    // 단일 컨테이너의 Docker Stats 데이터 수집 (요청 시 1회만)
    private MetricsResponseDTO collectSingleContainer(String containerId, String name) {
        try (StatsCmd statsCmd = dockerClient.statsCmd(containerId).withNoStream(true)) { // 단발성 모드
            final CountDownLatch latch = new CountDownLatch(1);
            final MetricsResponseDTO[] result = new MetricsResponseDTO[1];

            statsCmd.exec(new ResultCallback.Adapter<Statistics>() {
                @Override
                public void onNext(Statistics stats) {
                    double cpu = calculateCpuUsageInstant(stats); // 즉시값 기반 CPU 계산
                    double mem = calculateMemoryUsage(stats);
                    double net = calculateNetworkIO(stats);
                    double disk = calculateDiskIO(stats);

                    result[0] = new MetricsResponseDTO(name, cpu, mem, net, disk);
                    latch.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("Error while collecting stats for {}", name, throwable);
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    log.debug("Stats collection completed for {}", name);
                }
            });

            latch.await(2, TimeUnit.SECONDS);
            return result[0] != null ? result[0]
                    : new MetricsResponseDTO(name, 0, 0, 0, 0);

        } catch (Exception e) {
            log.error("Failed to collect stats for {}", name, e);
            return new MetricsResponseDTO(name, 0, 0, 0, 0);
        }
    }

    // 메트릭 계산 유틸리티
    // CPU 사용률 계산 (현재 스냅샷 기준) : PreCpuStats가 없는 단발성 수집에 적합
    private double calculateCpuUsageInstant(Statistics stats) {
        try {
            if (stats.getCpuStats() == null || stats.getCpuStats().getCpuUsage() == null)
                return 0.0;

            Long totalUsage = stats.getCpuStats().getCpuUsage().getTotalUsage();
            Long systemUsage = stats.getCpuStats().getSystemCpuUsage();
            Long cores = stats.getCpuStats().getOnlineCpus();

            if (totalUsage == null || systemUsage == null) return 0.0;
            if (cores == null) cores = 1L;

            // 절대값 기반 근사치
            return (totalUsage.doubleValue() / systemUsage.doubleValue()) * cores * 100.0;

        } catch (Exception e) {
            log.warn("Failed to calculate CPU usage: {}", e.getMessage());
            return 0.0;
        }
    }

    // 메모리 사용량 계산 (MB 단위)
    private double calculateMemoryUsage(Statistics stats) {
        try {
            if (stats.getMemoryStats() == null || stats.getMemoryStats().getUsage() == null)
                return 0.0;

            return stats.getMemoryStats().getUsage() / (1024.0 * 1024.0);
        } catch (Exception e) {
            log.warn("⚠️ Failed to calculate memory usage: {}", e.getMessage());
            return 0.0;
        }
    }

    // 네트워크 I/O 계산 (rx + tx → KB 단위)
    private double calculateNetworkIO(Statistics stats) {
        if (stats.getNetworks() == null) return 0.0;

        double total = 0.0;
        for (Map.Entry<String, StatisticNetworksConfig> entry : stats.getNetworks().entrySet()) {
            var net = entry.getValue();
            if (net.getRxBytes() != null) total += net.getRxBytes();
            if (net.getTxBytes() != null) total += net.getTxBytes();
        }

        return total / 1024.0;
    }

    // 디스크 I/O 계산 (blkioStats)
    private double calculateDiskIO(Statistics stats) {
        if (stats.getBlkioStats() == null ||
                stats.getBlkioStats().getIoServiceBytesRecursive() == null)
            return 0.0;

        return stats.getBlkioStats().getIoServiceBytesRecursive().stream()
                .mapToDouble(e -> e.getValue() != null ? e.getValue() : 0.0)
                .sum() / 1024.0;
    }
}