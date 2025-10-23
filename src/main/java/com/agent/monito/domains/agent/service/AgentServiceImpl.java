package com.agent.monito.domains.agent.service;

import com.agent.monito.domains.agent.dto.response.AgentStatusResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService{

    public AgentStatusResponseDTO getHealthStatus() {
        return AgentStatusResponseDTO.builder()
                .status("ok")
                .message("Agent is running normally")
                .build();
    }
}