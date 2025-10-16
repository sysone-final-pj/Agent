/**
 * Agent 서버의 상태를 확인하는 헬스체크 엔드포인트 제공
 * /api/health 요청 시 서버 동작 여부를 JSON 형태로 반환함.
 */
package com.agent.monito.controller;

import com.agent.monito.dto.response.VmStatusResponseDTO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    @GetMapping
    public VmStatusResponseDTO checkHealth() {

        return new VmStatusResponseDTO("OK", "Agent is running normally");
    }
}
