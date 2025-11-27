/**
 * 스케줄링 및 비동기 작업을 위한 설정 클래스
 * Backend로 메트릭을 전송하는 스케줄러의 스레드 풀을 관리함.
 * application.yml의 scheduler.thread-pool 설정값에 따라 스레드 풀 크기가 결정됨.
 */
package com.agent.monito.global.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
/**
 작성자: 백승준
 */
@Configuration
@EnableScheduling
@EnableAsync
@Slf4j
public class SchedulingConfig {

    @Value("${scheduler.thread-pool.core-size}")
    private int threadPoolCoreSize;

    @Value("${scheduler.thread-pool.max-size}")
    private int threadPoolMaxSize;

    @Value("${scheduler.thread-pool.queue-capacity}")
    private int threadPoolQueueCapacity;

    /**
     * Backend로 메트릭을 전송하는 스케줄러를 위한 비동기 실행 스레드 풀
     * - 스케줄링 작업을 비동기로 처리하여 블로킹 방지
     * - application.yml 파일에서 환경변수로 조정 가능
     */
    @Bean(name = "metricsPushExecutor")
    public Executor metricsPushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolCoreSize);
        executor.setMaxPoolSize(threadPoolMaxSize);
        executor.setQueueCapacity(threadPoolQueueCapacity);
        executor.setThreadNamePrefix("MetricsPush-");
        executor.setWaitForTasksToCompleteOnShutdown(true); // 종료 시 작업 완료 대기
        executor.setAwaitTerminationSeconds(30); // 최대 30초 대기
        executor.initialize();

        log.info("Scheduling Thread Pool Configuration Loaded:");
        log.info("   • Core Pool Size   : {}", threadPoolCoreSize);
        log.info("   • Max Pool Size    : {}", threadPoolMaxSize);
        log.info("   • Queue Capacity   : {}", threadPoolQueueCapacity);
        log.info("MetricsPush Executor successfully initialized");

        return executor;
    }
}
