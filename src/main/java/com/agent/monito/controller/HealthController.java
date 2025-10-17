/**
 * Agent 서버의 상태를 확인하는 헬스체크 엔드포인트 제공
 * /api/health 요청 시 서버 동작 여부를 JSON 형태로 반환함.
 */
package com.agent.monito.controller;

import com.agent.monito.dto.response.VmStatusResponseDTO;
import com.agent.monito.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping
    public ResponseEntity<VmStatusResponseDTO> checkHealth() {
        VmStatusResponseDTO status = healthService.getHealthStatus();
        return ResponseEntity.ok(status);
    }
}