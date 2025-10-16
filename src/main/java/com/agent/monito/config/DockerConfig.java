/**
 * .env 환경변수 기반으로 DockerClient를 Bean으로 등록하는 설정 클래스
 * 개발/운영 환경에 따라 Docker Daemon 접근 정보(TCP, TLS 등)를 안전하게 관리함.
 */
package com.agent.monito.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class DockerConfig {

    @Bean
    public DockerClient dockerClient() {

        // .env 파일 로드
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()  // (선택) .env 파일이 없을 경우 무시
                .load();

        // 필수 환경 변수 검증
        String dockerHost = dotenv.get("DOCKER_HOST");
        if (dockerHost == null || dockerHost.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: DOCKER_HOST");
        }

        // 선택적 환경 변수 (기본값 허용)
        boolean tlsVerify = Boolean.parseBoolean(dotenv.get("DOCKER_TLS_VERIFY", "false"));
        String certPath = dotenv.get("DOCKER_CERT_PATH", "");

        // Docker 설정 구성
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .withDockerTlsVerify(tlsVerify)
                .withDockerCertPath(certPath.isEmpty() ? null : certPath)
                .build();

        OkDockerHttpClient httpClient = new OkDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();

        // 로깅 (운영환경에서는 INFO 이하 레벨로 관리 가능)
        log.info("Docker Configuration Loaded:");
        log.info("   • Host       : {}", dockerHost);
        log.info("   • TLS Verify : {}", tlsVerify);
        if (!certPath.isEmpty()) log.info("   • Cert Path  : {}", certPath);

        // Bean 반환
        DockerClient client = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();

        log.info("Docker Client successfully initialized → {}", dockerHost);
        return client;
    }
}