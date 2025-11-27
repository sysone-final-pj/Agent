/**
 * Docker inspect 결과를 캐싱하여 반복적인 API 호출을 방지하는 클래스
 * Health status, OOMKilled 등의 정보는 자주 변하지 않으므로 TTL 기반으로 캐싱
 */
package com.agent.monito.domains.container.cache;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 작성자: 백승준
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InspectContainerCache {

    private final DockerClient dockerClient;

    /**
     * 캐시 TTL (초 단위) - 기본값 30초
     * application.yml에서 설정 가능
     */
    @Value("${docker.inspect.cache.ttl:30}")
    private int cacheTtlSeconds;

    /**
     * 컨테이너별 inspect 결과 캐시
     * Key: containerHash, Value: CachedInspectResponse
     */
    private final Map<String, CachedInspectResponse> inspectCache = new ConcurrentHashMap<>();

    /**
     * inspect 결과 조회 (캐시 우선, 없으면 Docker API 호출)
     * 캐시에 있고 유효하면 캐시 반환, 없거나 만료되면 Docker API 호출 후 캐싱
     *
     * @param containerHash 컨테이너 ID
     * @return InspectContainerResponse
     */
    public InspectContainerResponse getOrFetch(String containerHash) {
        // 캐시에서 먼저 조회
        InspectContainerResponse cached = get(containerHash);
        if (cached != null) {
            return cached;
        }

        // 캐시 미스 - Docker API 호출
        log.debug("Fetching inspect data from Docker API for container: {}", containerHash);
        InspectContainerResponse response = dockerClient.inspectContainerCmd(containerHash).exec();

        // 캐시에 저장
        put(containerHash, response);

        return response;
    }

    /**
     * inspect 결과 조회 (캐시된 데이터만 반환)
     *
     * @param containerHash 컨테이너 ID
     * @return InspectContainerResponse, 캐시에 없거나 만료되었으면 null
     */
    public InspectContainerResponse get(String containerHash) {
        CachedInspectResponse cached = inspectCache.get(containerHash);

        if (cached == null) {
            log.debug("Cache miss for container: {}", containerHash);
            return null;
        }

        // TTL 확인
        long now = Instant.now().getEpochSecond();
        if (now - cached.getCachedAt() > cacheTtlSeconds) {
            log.debug("Cache expired for container: {} (age: {}s, TTL: {}s)",
                    containerHash, now - cached.getCachedAt(), cacheTtlSeconds);
            inspectCache.remove(containerHash);
            return null;
        }

        log.debug("Cache hit for container: {} (age: {}s)",
                containerHash, now - cached.getCachedAt());
        return cached.getResponse();
    }

    /**
     * inspect 결과를 캐시에 저장
     *
     * @param containerHash 컨테이너 ID
     * @param response inspect 응답
     */
    public void put(String containerHash, InspectContainerResponse response) {
        CachedInspectResponse cached = new CachedInspectResponse(
                response,
                Instant.now().getEpochSecond()
        );
        inspectCache.put(containerHash, cached);
        log.debug("Cached inspect result for container: {}", containerHash);
    }

    /**
     * 특정 컨테이너의 캐시 제거
     *
     * @param containerHash 컨테이너 ID
     */
    public void remove(String containerHash) {
        CachedInspectResponse removed = inspectCache.remove(containerHash);
        if (removed != null) {
            log.debug("Removed cache for container: {}", containerHash);
        }
    }

    /**
     * 전체 캐시 초기화
     */
    public void clear() {
        log.info("Clearing all inspect cache");
        inspectCache.clear();
    }

    /**
     * 캐시 크기 조회
     *
     * @return 캐시된 컨테이너 수
     */
    public int size() {
        return inspectCache.size();
    }

    /**
     * 만료된 캐시 항목 정리
     * 스케줄러에서 주기적으로 호출하여 메모리 관리
     */
    public void evictExpired() {
        long now = Instant.now().getEpochSecond();
        int beforeSize = inspectCache.size();

        inspectCache.entrySet().removeIf(entry -> {
            long age = now - entry.getValue().getCachedAt();
            return age > cacheTtlSeconds;
        });

        int afterSize = inspectCache.size();
        if (beforeSize != afterSize) {
            log.info("Evicted {} expired inspect cache entries (before: {}, after: {})",
                    beforeSize - afterSize, beforeSize, afterSize);
        }
    }

    /**
     * 컨테이너의 health status 조회 (캐시 적용)
     *
     * @param containerHash 컨테이너 ID
     * @return health status (healthy, unhealthy, starting, none, unknown)
     */
    public String getHealthStatus(String containerHash) {
        try {
            InspectContainerResponse inspectResponse = getOrFetch(containerHash);
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
     * 캐시된 inspect 응답을 담는 내부 클래스
     */
    @Getter
    private static class CachedInspectResponse {
        private final InspectContainerResponse response;
        private final long cachedAt; // Unix timestamp (초)

        public CachedInspectResponse(InspectContainerResponse response, long cachedAt) {
            this.response = response;
            this.cachedAt = cachedAt;
        }
    }
}