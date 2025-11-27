/**
 * Docker 호스트 환경 정보를 캐싱하는 클래스
 * Docker가 실행되는 환경의 정적 정보(CPU, 메모리, 디스크)를 캐싱하여 반복 조회 방지
 */
package com.agent.monito.domains.agent.cache;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Info;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
/**
 작성자: 백승준
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerHostInfoCache {

    private final DockerClient dockerClient;

    // Docker 환경 정보 캐시 (애플리케이션 실행 중 변하지 않는 정보)
    private volatile Info cachedInfo = null;

    /**
     * Docker 호스트 정보 조회 (캐시 사용)
     * 최초 1회만 Docker API를 호출하고, 이후에는 캐시된 값 반환
     *
     * @return Docker Info 객체
     */
    public Info getDockerHostInfo() {
        if (cachedInfo == null) {
            synchronized (this) {
                if (cachedInfo == null) {
                    try {
                        cachedInfo = dockerClient.infoCmd().exec();
                        log.info("Docker host info cached - Total Memory: {} bytes, CPU Cores: {}",
                                cachedInfo.getMemTotal(), cachedInfo.getNCPU());
                    } catch (Exception e) {
                        log.error("Failed to fetch Docker host info", e);
                        throw new RuntimeException("Unable to retrieve Docker host information", e);
                    }
                }
            }
        }
        return cachedInfo;
    }

    /**
     * 캐시 초기화 (테스트 또는 강제 갱신 시 사용)
     */
    public void clearCache() {
        cachedInfo = null;
        log.info("Docker host info cache cleared");
    }

    /**
     * Docker 호스트의 전체 메모리 조회
     *
     * @return 전체 메모리 (bytes)
     */
    public Long getTotalMemory() {
        return getDockerHostInfo().getMemTotal();
    }

    /**
     * Docker 호스트의 CPU 코어 수 조회
     *
     * @return CPU 코어 수
     */
    public Integer getCpuCores() {
        return getDockerHostInfo().getNCPU();
    }
}