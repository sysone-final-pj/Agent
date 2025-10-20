/**
 * application.yml 기반으로 DockerClient를 Bean으로 등록하는 설정 클래스
 * 개발/운영 환경에 따라 Docker Daemon 접근 정보(TCP, TLS 등)를 안전하게 관리함.
 */
package com.agent.monito.global.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class DockerConfig {

    @Value("${docker.host}")
    private String dockerHost;

    @Value("${docker.tls-verify:false}")
    private boolean tlsVerify;

    @Value("${docker.cert-path:}")
    private String certPath;

    @Bean
    public DockerClient dockerClient() {
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