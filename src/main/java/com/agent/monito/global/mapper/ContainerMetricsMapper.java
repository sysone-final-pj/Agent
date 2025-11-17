/**
 * Docker Statistics를 DTO로 변환하는 Mapper 클래스
 * 원시 Statistics 데이터를 ContainerMetricsResponseDTO로 매핑
 */
package com.agent.monito.global.mapper;

import com.agent.monito.domains.agent.cache.DockerHostInfoCache;
import com.agent.monito.domains.container.dto.response.*;
import com.agent.monito.global.util.MetricsCalculator;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Map.Entry;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerMetricsMapper {

    private final MetricsCalculator metricsCalculator;
    private final DockerClient dockerClient;
    private final DockerHostInfoCache dockerHostInfoCache;

    // Cgroup v2 환경 여부 (한 번만 체크)
    private Boolean isCgroupV2 = null;

    /**
     * Statistics를 ContainerMetricsResponseDTO로 변환
     *
     * @param containerName 컨테이너 이름
     * @param stats         Docker Statistics
     * @return ContainerMetricsResponseDTO
     */
    public ContainerMetricsResponseDTO toSimpleMetricsDTO(String containerName, Statistics stats) {
        double cpu = metricsCalculator.calculateCpuUsageInstant(stats);
        double mem = metricsCalculator.calculateMemoryUsage(stats);
        double net = metricsCalculator.calculateNetworkIO(stats);
        double disk = metricsCalculator.calculateDiskIO(stats);

        return ContainerMetricsResponseDTO.builder()
                .containerName(containerName)
                .cpuUsage(cpu)
                .memoryUsage(mem)
                .networkIO(net)
                .diskIO(disk)
                .build();
    }

    /**
     * Statistics를 DetailedContainerMetricsResponseDTO로 변환
     *
     * @param containerHash 컨테이너 ID
     * @param containerName 컨테이너 이름
     * @param status        컨테이너 상태
     * @param state         컨테이너 state
     * @param health        컨테이너 health status
     * @param sizeRw        컨테이너 RW 사이즈
     * @param sizeRootFs    컨테이너 RootFs 사이즈
     * @param stats         Docker Statistics
     * @return DetailedContainerMetricsResponseDTO
     */
    public DetailedContainerMetricsResponseDTO toDetailedMetricsDTO(
            String containerHash,
            String containerName,
            String status,
            String state,
            String health,
            Long sizeRw,
            Long sizeRootFs,
            Statistics stats) {

        return DetailedContainerMetricsResponseDTO.builder()
                .containerHash(containerHash)
                .containerName(containerName)
                .status(status)
                .state(state)
                .health(health)
                .collectedAt(LocalDateTime.now())
                .cpu(buildCpuMetrics(stats, containerHash))
                .memory(buildMemoryMetrics(stats, containerHash))
                .network(buildNetworkMetrics(stats))
                .blockIO(buildBlockIOMetrics(stats))
                .storage(buildStorageMetrics(containerHash, sizeRw, sizeRootFs))
                .build();
    }

    /**
     * 빈 상세 메트릭 DTO 생성 (에러 발생 시)
     */
    public DetailedContainerMetricsResponseDTO buildEmptyDetailedMetrics(
            String containerHash,
            String name,
            String status,
            Long sizeRw,
            Long sizeRootFs) {

        return DetailedContainerMetricsResponseDTO.builder()
                .containerHash(containerHash)
                .containerName(name)
                .status(status)
                .collectedAt(LocalDateTime.now())
                .cpu(CpuMetricsResponseDTO.builder().build())
                .memory(MemoryMetricsResponseDTO.builder().build())
                .network(NetworkMetricsResponseDTO.builder().build())
                .blockIO(BlockIOMetricsResponseDTO.builder()
                        .blkRead(0L)
                        .blkWrite(0L)
                        .build())
                .storage(StorageMetricsResponseDTO.builder()
                        .sizeRw(sizeRw != null ? sizeRw : 0L)
                        .sizeRootFs(sizeRootFs != null ? sizeRootFs : 0L)
                        .storageLimit(0L)
                        .isStorageUnlimited(true)  // 에러 발생 시 무제한으로 간주
                        .imageSize(0L)
                        .imageName("unknown")
                        .build())
                .build();
    }

    // ========== Private Helper Methods ==========

    private CpuMetricsResponseDTO buildCpuMetrics(Statistics stats, String containerHash) {
        if (stats.getCpuStats() == null) {
            return CpuMetricsResponseDTO.builder().build();
        }

        CpuStatsConfig cpuStats = stats.getCpuStats();
        CpuUsageConfig cpuUsage = cpuStats.getCpuUsage();

        Long totalUsage = cpuUsage != null ? cpuUsage.getTotalUsage() : null;
        Long systemUsage = cpuStats.getSystemCpuUsage();
        Long onlineCpus = cpuStats.getOnlineCpus();

        Long cpuUser = null;
        Long cpuSystem = null;
        if (cpuUsage != null) {
            cpuUser = cpuUsage.getUsageInUsermode();
            cpuSystem = cpuUsage.getUsageInKernelmode();
        }

        ThrottlingDataConfig throttlingData = cpuStats.getThrottlingData();
        Long throttlingPeriods = throttlingData != null ? throttlingData.getPeriods() : 0L;
        Long throttledPeriods = throttlingData != null ? throttlingData.getThrottledPeriods() : 0L;
        Long throttledTime = throttlingData != null ? throttlingData.getThrottledTime() : 0L;

        Long cpuQuota = null;
        Long cpuPeriod = null;
        try {
            InspectContainerResponse inspectResponse =
                    dockerClient.inspectContainerCmd(containerHash).exec();
            HostConfig hostConfig = inspectResponse.getHostConfig();
            if (hostConfig != null) {
                cpuQuota = hostConfig.getCpuQuota();
                cpuPeriod = hostConfig.getCpuPeriod();

                // --cpus 옵션으로 설정된 경우 NanoCPUs 확인
                if ((cpuQuota == null || cpuQuota <= 0) && hostConfig.getNanoCPUs() != null) {
                    Long nanoCpus = hostConfig.getNanoCPUs();
                    if (nanoCpus > 0) {
                        // NanoCPUs는 10^9 단위 (1 CPU = 1,000,000,000 nano CPUs)
                        // cpuQuota = (nanoCpus / 10^9) * cpuPeriod
                        cpuPeriod = (cpuPeriod != null && cpuPeriod > 0) ? cpuPeriod : 100000L;
                        cpuQuota = (nanoCpus * cpuPeriod) / 1_000_000_000L;
                        log.debug("CPU limit from NanoCPUs for container {}: nanoCpus={}, calculated quota={}",
                                containerHash, nanoCpus, cpuQuota);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get CPU quota/period for container {}: {}", containerHash, e.getMessage());
        }

        if (cpuPeriod == null || cpuPeriod == 0) {
            cpuPeriod = 100000L;
        }

        // CPU 제한 여부 판단
        boolean isCpuUnlimited = (cpuQuota == null || cpuQuota <= 0);

        // cpuQuota가 0이면 무제한 -> 호스트 전체 CPU로 대체
        if (isCpuUnlimited) {
            Integer hostCpuCores = dockerHostInfoCache.getCpuCores();
            cpuQuota = hostCpuCores * cpuPeriod;  // 예: 8코어 * 100000 = 800000
            log.debug("CPU quota is unlimited for container {}, using host CPU: {} cores (quota: {})",
                    containerHash, hostCpuCores, cpuQuota);
        }

        return CpuMetricsResponseDTO.builder()
                .cpuUsageTotal(totalUsage)
                .cpuUser(cpuUser)
                .cpuSystem(cpuSystem)
                .systemCpuUsage(systemUsage)
                .onlineCpus(onlineCpus)
                .cpuQuota(cpuQuota)
                .cpuPeriod(cpuPeriod)
                .isCpuUnlimited(isCpuUnlimited)
                .throttlingPeriods(throttlingPeriods)
                .throttledPeriods(throttledPeriods)
                .throttledTime(throttledTime)
                .build();
    }

    private MemoryMetricsResponseDTO buildMemoryMetrics(Statistics stats, String containerHash) {
        if (stats.getMemoryStats() == null) {
            return MemoryMetricsResponseDTO.builder().build();
        }

        MemoryStatsConfig memStats = stats.getMemoryStats();
        Long memUsage = memStats.getUsage();
        Long memLimit = memStats.getLimit();
        Long memMaxUsage = memStats.getMaxUsage();

        if (memMaxUsage == null) {
            if (isCgroupV2 == null) {
                log.warn("⚠️ memory.max_usage is null - Cgroup v2 environment detected.");
                log.warn("   → Backend will handle peak memory tracking instead.");
                isCgroupV2 = true;
            }
            memMaxUsage = 0L;
        } else if (isCgroupV2 == null) {
            isCgroupV2 = false;
        }

        // Inspect API로 메모리 제한 여부 판단
        boolean isMemoryUnlimited = false;
        try {
            InspectContainerResponse inspectResponse =
                    dockerClient.inspectContainerCmd(containerHash).exec();
            HostConfig hostConfig = inspectResponse.getHostConfig();
            if (hostConfig != null) {
                Long configuredMemory = hostConfig.getMemory();
                // Memory가 0이거나 null이면 무제한
                isMemoryUnlimited = (configuredMemory == null || configuredMemory == 0);
                log.debug("Memory limit for container {}: configured={}, unlimited={}",
                        containerHash, configuredMemory, isMemoryUnlimited);
            }
        } catch (Exception e) {
            log.warn("Failed to get memory limit for container {}: {}", containerHash, e.getMessage());
        }

        // memLimit가 null이면 무제한 -> 호스트 전체 메모리로 대체
        if (isMemoryUnlimited && memLimit == null) {
            memLimit = dockerHostInfoCache.getTotalMemory();
            log.debug("Memory limit is unlimited for container {}, using host memory: {} bytes",
                    containerHash, memLimit);
        }

        log.debug("Memory stats - usage: {}, limit: {}, max_usage: {}, unlimited: {}",
                memUsage, memLimit, memMaxUsage, isMemoryUnlimited);

        return MemoryMetricsResponseDTO.builder()
                .memUsage(memUsage)
                .memLimit(memLimit)
                .memMaxUsage(memMaxUsage)
                .isMemoryUnlimited(isMemoryUnlimited)
                .build();
    }

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

        for (Entry<String, StatisticNetworksConfig> entry : stats.getNetworks().entrySet()) {
            StatisticNetworksConfig net = entry.getValue();
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

    private BlockIOMetricsResponseDTO buildBlockIOMetrics(Statistics stats) {
        if (stats.getBlkioStats() == null || stats.getBlkioStats().getIoServiceBytesRecursive() == null) {
            return BlockIOMetricsResponseDTO.builder()
                    .blkRead(0L)
                    .blkWrite(0L)
                    .build();
        }

        long blkRead = 0L;
        long blkWrite = 0L;

        for (BlkioStatEntry entry : stats.getBlkioStats().getIoServiceBytesRecursive()) {
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

    private StorageMetricsResponseDTO buildStorageMetrics(String containerHash, Long sizeRw, Long sizeRootFs) {
        try {
            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerHash).exec();
            String imageId = containerInfo.getImageId();
            String imageName = containerInfo.getConfig().getImage();
            Long imageSize = 0L;
            Long storageLimit = 0L;

            // Get image size
            if (imageId != null) {
                try {
                    InspectImageResponse imageInfo = dockerClient.inspectImageCmd(imageId).exec();
                    imageSize = imageInfo.getSize();
                } catch (Exception e) {
                    log.warn("Failed to get image size for container {}: {}", containerHash, e.getMessage());
                }
            }

            // Get storage limit from HostConfig.StorageOpt
            HostConfig hostConfig = containerInfo.getHostConfig();
            if (hostConfig != null && hostConfig.getStorageOpt() != null) {
                Map<String, String> storageOpt = hostConfig.getStorageOpt();
                String sizeStr = storageOpt.get("size");
                if (sizeStr != null && !sizeStr.isEmpty()) {
                    storageLimit = parseStorageSize(sizeStr);
                    log.debug("Storage limit found for container {}: {} ({})", containerHash, sizeStr, storageLimit);
                }
            }

            // Storage 제한 여부 판단
            boolean isStorageUnlimited = (storageLimit == 0);

            log.debug("Storage metrics - sizeRw: {}, sizeRootFs: {}, imageSize: {}, storageLimit: {}, unlimited: {}, imageName: {}",
                    sizeRw, sizeRootFs, imageSize, storageLimit, isStorageUnlimited, imageName);

            return StorageMetricsResponseDTO.builder()
                    .sizeRw(sizeRw != null ? sizeRw : 0L)
                    .sizeRootFs(sizeRootFs != null ? sizeRootFs : 0L)
                    .storageLimit(storageLimit)
                    .isStorageUnlimited(isStorageUnlimited)
                    .imageSize(imageSize)
                    .imageName(imageName)
                    .build();

        } catch (Exception e) {
            log.error("Failed to collect storage metrics for container {}: {}", containerHash, e.getMessage());
            return StorageMetricsResponseDTO.builder()
                    .sizeRw(sizeRw != null ? sizeRw : 0L)
                    .sizeRootFs(sizeRootFs != null ? sizeRootFs : 0L)
                    .storageLimit(0L)
                    .isStorageUnlimited(true)  // 에러 발생 시 무제한으로 간주
                    .imageSize(0L)
                    .imageName("unknown")
                    .build();
        }
    }

    /**
     * Docker storage size 문자열을 bytes로 변환
     * 예: "10G" -> 10737418240, "100M" -> 104857600, "1024K" -> 1048576, "1234" -> 1234
     *
     * @param sizeStr Docker storage size 문자열
     * @return bytes 값, 파싱 실패 시 0L
     */
    private Long parseStorageSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) {
            return 0L;
        }

        try {
            sizeStr = sizeStr.trim().toUpperCase();

            // 숫자만 있는 경우 (bytes)
            if (sizeStr.matches("^\\d+$")) {
                return Long.parseLong(sizeStr);
            }

            // 단위가 있는 경우
            String numPart = sizeStr.replaceAll("[^0-9.]", "");
            String unitPart = sizeStr.replaceAll("[0-9.]", "");

            double num = Double.parseDouble(numPart);
            long multiplier = 1L;

            switch (unitPart) {
                case "K":
                case "KB":
                    multiplier = 1024L;
                    break;
                case "M":
                case "MB":
                    multiplier = 1024L * 1024L;
                    break;
                case "G":
                case "GB":
                    multiplier = 1024L * 1024L * 1024L;
                    break;
                case "T":
                case "TB":
                    multiplier = 1024L * 1024L * 1024L * 1024L;
                    break;
                case "B":
                    multiplier = 1L;
                    break;
                default:
                    log.warn("Unknown storage size unit: {}, treating as bytes", unitPart);
                    multiplier = 1L;
            }

            return (long) (num * multiplier);

        } catch (Exception e) {
            log.warn("Failed to parse storage size '{}': {}", sizeStr, e.getMessage());
            return 0L;
        }
    }
}