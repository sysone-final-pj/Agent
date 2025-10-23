/**
 * Docker API를 통해 실행 중인 모든 컨테이너의 CPU, 메모리, 네트워크, 디스크 I/O 등의 메트릭을 수집하는 클래스
 * 각 컨테이너별 실시간 통계를 단발성으로 가져와 DTO 형태로 반환함.
 */
package com.agent.monito.domains.container.collector;

import com.agent.monito.domains.container.dto.response.*;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.CpuStatsConfig;
import com.github.dockerjava.api.model.CpuUsageConfig;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StatisticNetworksConfig;
import com.github.dockerjava.api.model.ThrottlingDataConfig;
import java.util.Map.Entry;
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

    // 실행 중인 모든 컨테이너의 메트릭을 병렬로 수집
    public List<ContainerMetricsResponseDTO> collectAllContainers() {
        try {
            List<Container> containers = dockerClient.listContainersCmd().exec();
            log.info("Found {} running containers", containers.size());

            // 모든 컨테이너를 병렬로 수집
            List<CompletableFuture<ContainerMetricsResponseDTO>> futures = containers.stream()
                    .map(container -> CompletableFuture.supplyAsync(() -> {
                        String containerId = container.getId();
                        String containerName = container.getNames()[0].replace("/", "");
                        log.info("Collecting stats for container: {}", containerName);
                        return collectSingleContainer(containerId, containerName);
                    }))
                    .collect(Collectors.toList());

            // 모든 결과를 기다린 후 반환 (개별 컨테이너 실패 시에도 계속 진행)
            return futures.stream()
                    .map(future -> {
                        try {
                            return future.join();
                        } catch (Exception e) {
                            log.error("Failed to collect container metrics", e);
                            return null;
                        }
                    })
                    .filter(metrics -> metrics != null)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error collecting container stats", e);
            return new ArrayList<>();
        }
    }

    // 실행 중인 모든 컨테이너의 상세 메트릭을 병렬로 수집
    public List<DetailedContainerMetricsResponseDTO> collectAllDetailedContainers() {
        try {
            List<Container> containers = dockerClient.listContainersCmd().exec();
            log.info("Found {} running containers", containers.size());

            // 모든 컨테이너를 병렬로 수집
            List<CompletableFuture<DetailedContainerMetricsResponseDTO>> futures = containers.stream()
                    .map(container -> CompletableFuture.supplyAsync(() -> {
                        String containerId = container.getId();
                        String containerName = container.getNames()[0].replace("/", "");
                        String status = container.getStatus();
                        String state = container.getState();
                        log.info("Collecting detailed stats for container: {}", containerName);
                        return collectSingleDetailedContainer(containerId, containerName, status, state);
                    }))
                    .collect(Collectors.toList());

            // 모든 결과를 기다린 후 반환 (개별 컨테이너 실패 시에도 계속 진행)
            return futures.stream()
                    .map(future -> {
                        try {
                            return future.join();
                        } catch (Exception e) {
                            log.error("Failed to collect detailed container metrics", e);
                            return null;
                        }
                    })
                    .filter(metrics -> metrics != null)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error collecting detailed container stats", e);
            return new ArrayList<>();
        }
    }

    // 단일 컨테이너의 Docker Stats 데이터 수집 (요청 시 1회만)
    private ContainerMetricsResponseDTO collectSingleContainer(String containerId, String name) {
        try (StatsCmd statsCmd = dockerClient.statsCmd(containerId).withNoStream(true)) { // 단발성 모드
            final CountDownLatch latch = new CountDownLatch(1);
            final ContainerMetricsResponseDTO[] result = new ContainerMetricsResponseDTO[1];

            statsCmd.exec(new ResultCallback.Adapter<Statistics>() {
                @Override
                public void onNext(Statistics stats) {
                    double cpu = calculateCpuUsageInstant(stats); // 즉시값 기반 CPU 계산
                    double mem = calculateMemoryUsage(stats);
                    double net = calculateNetworkIO(stats);
                    double disk = calculateDiskIO(stats);

                    result[0] = ContainerMetricsResponseDTO.builder()
                            .containerName(name)
                            .cpuUsage(cpu)
                            .memoryUsage(mem)
                            .networkIO(net)
                            .diskIO(disk)
                            .build();
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
                    : ContainerMetricsResponseDTO.builder()
                            .containerName(name)
                            .cpuUsage(0)
                            .memoryUsage(0)
                            .networkIO(0)
                            .diskIO(0)
                            .build();

        } catch (Exception e) {
            log.error("Failed to collect stats for {}", name, e);
            return ContainerMetricsResponseDTO.builder()
                    .containerName(name)
                    .cpuUsage(0)
                    .memoryUsage(0)
                    .networkIO(0)
                    .diskIO(0)
                    .build();
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
        for (Entry<String, StatisticNetworksConfig> entry : stats.getNetworks().entrySet()) {
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

    // 단일 컨테이너의 상세 메트릭 수집
    private DetailedContainerMetricsResponseDTO collectSingleDetailedContainer(String containerHash, String name, String status, String state) {
        try (StatsCmd statsCmd = dockerClient.statsCmd(containerHash).withNoStream(true)) {
            final CountDownLatch latch = new CountDownLatch(1);
            final DetailedContainerMetricsResponseDTO[] result = new DetailedContainerMetricsResponseDTO[1];

            statsCmd.exec(new ResultCallback.Adapter<Statistics>() {
                @Override
                public void onNext(Statistics stats) {
                    result[0] = DetailedContainerMetricsResponseDTO.builder()
                            .containerHash(containerHash)
                            .containerName(name)
                            .status(status)
                            .state(state)
                            .cpu(buildCpuMetrics(stats, containerHash))
                            .memory(buildMemoryMetrics(stats))
                            .network(buildNetworkMetrics(stats))
                            .blockIO(buildBlockIOMetrics(stats))
                            .build();
                    latch.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("Error while collecting detailed stats for {}", name, throwable);
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    log.debug("Detailed stats collection completed for {}", name);
                }
            });

            latch.await(2, TimeUnit.SECONDS);
            return result[0] != null ? result[0] : buildEmptyDetailedMetrics(containerHash, name, status);

        } catch (Exception e) {
            log.error("Failed to collect detailed stats for {}", name, e);
            return buildEmptyDetailedMetrics(containerHash, name, status);
        }
    }

    // CPU 상세 메트릭 빌드 - 원시 데이터만 수집
    private CpuMetricsResponseDTO buildCpuMetrics(Statistics stats, String containerId) {
        if (stats.getCpuStats() == null) {
            return CpuMetricsResponseDTO.builder().build();
        }

        CpuStatsConfig cpuStats = stats.getCpuStats();
        CpuUsageConfig cpuUsage = cpuStats.getCpuUsage();

        // 원시 데이터 추출 (계산 없음)
        Long totalUsage = cpuUsage != null ? cpuUsage.getTotalUsage() : null;
        Long systemUsage = cpuStats.getSystemCpuUsage();
        Long onlineCpus = cpuStats.getOnlineCpus();

        // User/System CPU 사용량
        Long cpuUser = null;
        Long cpuSystem = null;
        if (cpuUsage != null) {
            cpuUser = cpuUsage.getUsageInUsermode();
            cpuSystem = cpuUsage.getUsageInKernelmode();
        }

        // Throttling 정보
        ThrottlingDataConfig throttlingData = cpuStats.getThrottlingData();
        Long throttlingPeriods = throttlingData != null ? throttlingData.getPeriods() : null;
        Long throttledPeriods = throttlingData != null ? throttlingData.getThrottledPeriods() : null;
        Long throttledTime = throttlingData != null ? throttlingData.getThrottledTime() : null;

        // CPU Quota와 Period는 inspect API에서 가져옴
        Long cpuQuota = null;
        Long cpuPeriod = null;
        try {
            InspectContainerResponse inspectResponse =
                dockerClient.inspectContainerCmd(containerId).exec();
            HostConfig hostConfig = inspectResponse.getHostConfig();
            if (hostConfig != null) {
                cpuQuota = hostConfig.getCpuQuota();
                cpuPeriod = hostConfig.getCpuPeriod();
            }
        } catch (Exception e) {
            log.warn("Failed to get CPU quota/period for container {}: {}", containerId, e.getMessage());
        }

        return CpuMetricsResponseDTO.builder()
                .cpuUsageTotal(totalUsage)
                .cpuUser(cpuUser)
                .cpuSystem(cpuSystem)
                .systemCpuUsage(systemUsage)
                .onlineCpus(onlineCpus)
                .cpuQuota(cpuQuota)
                .cpuPeriod(cpuPeriod)
                .throttlingPeriods(throttlingPeriods)
                .throttledPeriods(throttledPeriods)
                .throttledTime(throttledTime)
                .build();
    }

    // Memory 상세 메트릭 빌드 - 원시 데이터만 수집
    private MemoryMetricsResponseDTO buildMemoryMetrics(Statistics stats) {
        if (stats.getMemoryStats() == null) {
            return MemoryMetricsResponseDTO.builder().build();
        }

        var memStats = stats.getMemoryStats();

        // 원시 데이터 추출 (계산 없음)
        Long memUsage = memStats.getUsage();
        Long memLimit = memStats.getLimit();
        Long memMaxUsage = memStats.getMaxUsage();

        return MemoryMetricsResponseDTO.builder()
                .memUsage(memUsage)
                .memLimit(memLimit)
                .memMaxUsage(memMaxUsage)
                .build();
    }

    // Network 상세 메트릭 빌드 - 원시 데이터만 수집
    private NetworkMetricsResponseDTO buildNetworkMetrics(Statistics stats) {
        if (stats.getNetworks() == null || stats.getNetworks().isEmpty()) {
            return NetworkMetricsResponseDTO.builder().build();
        }

        long rxBytes = 0L;
        long txBytes = 0L;
        long rxPackets = 0L;
        long txPackets = 0L;
        long rxErrors = 0L;
        long txErrors = 0L;
        long rxDropped = 0L;
        long txDropped = 0L;

        // 모든 네트워크 인터페이스의 원시 데이터를 합산
        for (Entry<String, StatisticNetworksConfig> entry : stats.getNetworks().entrySet()) {
            var net = entry.getValue();
            if (net.getRxBytes() != null) rxBytes += net.getRxBytes();
            if (net.getTxBytes() != null) txBytes += net.getTxBytes();
            if (net.getRxPackets() != null) rxPackets += net.getRxPackets();
            if (net.getTxPackets() != null) txPackets += net.getTxPackets();
            if (net.getRxErrors() != null) rxErrors += net.getRxErrors();
            if (net.getTxErrors() != null) txErrors += net.getTxErrors();
            if (net.getRxDropped() != null) rxDropped += net.getRxDropped();
            if (net.getTxDropped() != null) txDropped += net.getTxDropped();
        }

        return NetworkMetricsResponseDTO.builder()
                .rxBytes(rxBytes)
                .txBytes(txBytes)
                .rxPackets(rxPackets)
                .txPackets(txPackets)
                .rxErrors(rxErrors)
                .txErrors(txErrors)
                .rxDropped(rxDropped)
                .txDropped(txDropped)
                .build();
    }

    // Block I/O 상세 메트릭 빌드 - 원시 데이터만 수집
    private BlockIOMetricsResponseDTO buildBlockIOMetrics(Statistics stats) {
        if (stats.getBlkioStats() == null || stats.getBlkioStats().getIoServiceBytesRecursive() == null) {
            return BlockIOMetricsResponseDTO.builder().build();
        }

        long blkRead = 0L;
        long blkWrite = 0L;

        // Read/Write 원시 데이터 추출
        for (var entry : stats.getBlkioStats().getIoServiceBytesRecursive()) {
            if (entry.getOp() != null && entry.getValue() != null) {
                if (entry.getOp().equalsIgnoreCase("Read")) {
                    blkRead += entry.getValue();
                } else if (entry.getOp().equalsIgnoreCase("Write")) {
                    blkWrite += entry.getValue();
                }
            }
        }

        return BlockIOMetricsResponseDTO.builder()
                .blkRead(blkRead)
                .blkWrite(blkWrite)
                .build();
    }

    // 빈 상세 메트릭 객체 생성 (에러 발생 시)
    private DetailedContainerMetricsResponseDTO buildEmptyDetailedMetrics(String containerHash, String name, String status) {
        return DetailedContainerMetricsResponseDTO.builder()
                .containerHash(containerHash)
                .containerName(name)
                .status(status)
                .cpu(CpuMetricsResponseDTO.builder().build())
                .memory(MemoryMetricsResponseDTO.builder().build())
                .network(NetworkMetricsResponseDTO.builder().build())
                .blockIO(BlockIOMetricsResponseDTO.builder().build())
                .build();
    }
}