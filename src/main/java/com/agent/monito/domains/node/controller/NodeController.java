/**
 * Agent 서버의 상태를 확인하는 헬스체크 엔드포인트 제공
 * /api/health 요청 시 서버 동작 여부를 JSON 형태로 반환함.
 */
package com.agent.monito.domains.node.controller;

import com.agent.monito.domains.node.dto.response.NodeStatusResponseDTO;
import com.agent.monito.domains.node.service.NodeService;
import com.agent.monito.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/node")
@RequiredArgsConstructor
public class NodeController {

    private final NodeService nodeService;

    @GetMapping("status")
    public ApiResponse<NodeStatusResponseDTO> checkHealth() {
        NodeStatusResponseDTO status = nodeService.getHealthStatus();
        return ApiResponse.ok(status);
    }
}