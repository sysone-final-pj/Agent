/**
 * 컨테이너 메트릭 계산 유틸리티 클래스
 * Docker Statistics 데이터를 기반으로 CPU, 메모리, 네트워크, 디스크 사용률 계산
 */
package com.agent.monito.global.util;

import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StatisticNetworksConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map.Entry;
/**
 작성자: 백승준
 */
@Slf4j
@Component
public class MetricsCalculator {

    /**
     * CPU 사용률 계산 (현재 스냅샷 기준)
     * PreCpuStats가 없는 단발성 수집에 적합
     *
     * @param stats Docker Statistics
     * @return CPU 사용률 (%)
     */
    public double calculateCpuUsageInstant(Statistics stats) {
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

    /**
     * 메모리 사용량 계산 (MB 단위)
     *
     * @param stats Docker Statistics
     * @return 메모리 사용량 (MB)
     */
    public double calculateMemoryUsage(Statistics stats) {
        try {
            if (stats.getMemoryStats() == null || stats.getMemoryStats().getUsage() == null)
                return 0.0;

            return stats.getMemoryStats().getUsage() / (1024.0 * 1024.0);
        } catch (Exception e) {
            log.warn("Failed to calculate memory usage: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 네트워크 I/O 계산 (rx + tx → KB 단위)
     *
     * @param stats Docker Statistics
     * @return 네트워크 I/O (KB)
     */
    public double calculateNetworkIO(Statistics stats) {
        if (stats.getNetworks() == null) return 0.0;

        double total = 0.0;
        for (Entry<String, StatisticNetworksConfig> entry : stats.getNetworks().entrySet()) {
            StatisticNetworksConfig net = entry.getValue();
            if (net.getRxBytes() != null) total += net.getRxBytes();
            if (net.getTxBytes() != null) total += net.getTxBytes();
        }

        return total / 1024.0;
    }

    /**
     * 디스크 I/O 계산 (blkioStats → KB 단위)
     *
     * @param stats Docker Statistics
     * @return 디스크 I/O (KB)
     */
    public double calculateDiskIO(Statistics stats) {
        if (stats.getBlkioStats() == null ||
                stats.getBlkioStats().getIoServiceBytesRecursive() == null)
            return 0.0;

        return stats.getBlkioStats().getIoServiceBytesRecursive().stream()
                .mapToDouble(e -> e.getValue() != null ? e.getValue() : 0.0)
                .sum() / 1024.0;
    }
}