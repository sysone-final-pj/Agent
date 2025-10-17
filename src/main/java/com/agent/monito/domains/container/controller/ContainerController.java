/**
 * 중앙 백엔드의 요청에 따라 DockerMetricsCollector를 호출해 컨테이너 메트릭을 수집 후 응답하는 REST 컨트롤러
 * /api/metrics 엔드포인트를 통해 모든 컨테이너의 자원 사용량을 반환함.
 */
package com.agent.monito.domains.container.controller;

import com.agent.monito.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agent.monito.domains.container.dto.response.ContainerMetricsResponseDTO;
import com.agent.monito.domains.container.service.ContainerService;

import java.util.List;

@RestController
@RequestMapping("/api/containers")
@RequiredArgsConstructor
public class ContainerController {

    private final ContainerService containerService;

    @GetMapping
    public ApiResponse<List<ContainerMetricsResponseDTO>> getAllMetrics() {
        List<ContainerMetricsResponseDTO> metrics = containerService.collectAllContainerMetrics();
        return ApiResponse.ok(metrics, "컨테이너 정보 응답");
    }
}