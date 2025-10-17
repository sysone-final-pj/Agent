package com.agent.monito.domains.node.service;

import com.agent.monito.domains.node.dto.response.NodeStatusResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NodeService {

    public NodeStatusResponseDTO getHealthStatus() {
        return NodeStatusResponseDTO.builder()
                .status("ok")
                .message("Agent is running normally")
                .build();
    }
}