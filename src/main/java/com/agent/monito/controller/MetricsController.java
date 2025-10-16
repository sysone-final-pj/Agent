/**
 * 중앙 백엔드의 요청에 따라 DockerMetricsCollector를 호출해 컨테이너 메트릭을 수집 후 응답하는 REST 컨트롤러
 * /api/metrics 엔드포인트를 통해 모든 컨테이너의 자원 사용량을 반환함.
 */
package com.agent.monito.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agent.monito.dto.response.MetricsResponseDTO;
import com.agent.monito.service.MetricsService;

import java.util.List;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping
    public ResponseEntity<List<MetricsResponseDTO>> getAllMetrics() {
        List<MetricsResponseDTO> metrics = metricsService.collectAllContainerMetrics();
        return ResponseEntity.ok(metrics);
    }
}