/**
 * Agent 서버의 상태를 확인하는 헬스체크 엔드포인트 제공
 * /api/health 요청 시 서버 동작 여부를 JSON 형태로 반환함.
 */
package com.agent.monito.domains.agent.controller;

import com.agent.monito.domains.agent.dto.response.AgentStatusResponseDTO;
import com.agent.monito.domains.agent.service.AgentService;
import com.agent.monito.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping("status")
    public ApiResponse<AgentStatusResponseDTO> checkHealth() {
        AgentStatusResponseDTO status = agentService.getHealthStatus();
        return ApiResponse.ok(status);
    }
}