/**
 * 컨테이너별 마지막 로그 수집 시점을 관리하는 캐시
 * 중복 로그 수집 방지를 위해 since 파라미터로 사용할 timestamp를 저장
 */
package com.agent.monito.domains.container.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 작성자: 백승준
 */
@Slf4j
@Component
public class ContainerLogTimestampCache {

    /**
     * 컨테이너별 마지막 로그 수집 시점 (Unix timestamp - 초 단위)
     * Key: containerHash, Value: 마지막 로그 timestamp (초)
     */
    private final Map<String, Integer> containerTimestamps = new ConcurrentHashMap<>();

    /**
     * 특정 컨테이너의 마지막 로그 수집 시점 조회
     *
     * @param containerHash 컨테이너 ID
     * @return Unix timestamp (초 단위), 없으면 null
     */
    public Integer getLastTimestamp(String containerHash) {
        return containerTimestamps.get(containerHash);
    }

    /**
     * 모든 컨테이너의 마지막 로그 수집 시점 조회
     *
     * @return containerHash -> timestamp 맵
     */
    public Map<String, Integer> getAllTimestamps() {
        return new HashMap<>(containerTimestamps);
    }

    /**
     * 로그 수집 후 컨테이너별 타임스탬프 업데이트
     * 각 컨테이너의 로그 중 가장 최신 timestamp를 저장
     *
     * @param containerHash 컨테이너 ID
     * @param logTimestamp 로그 타임스탬프 문자열 (ISO 8601 형식)
     */
    public void updateTimestamp(String containerHash, String logTimestamp) {
        try {
            // ISO 8601 형식의 timestamp를 Unix timestamp (초)로 변환
            // 예: "2025-10-29T02:59:32.248648493Z" -> Unix timestamp
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(logTimestamp, DateTimeFormatter.ISO_DATE_TIME);
            int unixTimestamp = (int) zonedDateTime.toEpochSecond();

            // 기존 값보다 큰 경우에만 업데이트 (최신 값 유지)
            containerTimestamps.merge(containerHash, unixTimestamp, Math::max);

            log.debug("Updated timestamp for container {}: {} ({})",
                    containerHash, unixTimestamp, logTimestamp);

        } catch (Exception e) {
            log.warn("Failed to parse timestamp '{}' for container {}: {}",
                    logTimestamp, containerHash, e.getMessage());
        }
    }

    /**
     * 현재 시각을 Unix timestamp로 저장 (로그가 없는 경우 사용)
     *
     * @param containerHash 컨테이너 ID
     */
    public void updateToNow(String containerHash) {
        int now = (int) Instant.now().getEpochSecond();
        containerTimestamps.put(containerHash, now);
        log.debug("Updated timestamp for container {} to now: {}", containerHash, now);
    }

    /**
     * 특정 컨테이너의 타임스탬프 제거 (컨테이너 종료 시 사용)
     *
     * @param containerHash 컨테이너 ID
     */
    public void remove(String containerHash) {
        Integer removed = containerTimestamps.remove(containerHash);
        if (removed != null) {
            log.debug("Removed timestamp for container {}: {}", containerHash, removed);
        }
    }

    /**
     * 실행 중인 컨테이너 목록과 동기화
     * 더 이상 실행되지 않는 컨테이너의 타임스탬프를 정리
     *
     * @param runningContainerHashes 현재 실행 중인 컨테이너 ID 목록
     */
    public void syncWithRunningContainers(List<String> runningContainerHashes) {
        // 캐시에는 있지만 실행 중이 아닌 컨테이너 찾기
        containerTimestamps.keySet().removeIf(hash -> {
            if (!runningContainerHashes.contains(hash)) {
                log.info("Removing timestamp for stopped container: {}", hash);
                return true;
            }
            return false;
        });
    }

    /**
     * 전체 캐시 초기화
     */
    public void clear() {
        log.info("Clearing all container timestamps");
        containerTimestamps.clear();
    }

    /**
     * 캐시 크기 조회
     *
     * @return 캐시된 컨테이너 수
     */
    public int size() {
        return containerTimestamps.size();
    }
}