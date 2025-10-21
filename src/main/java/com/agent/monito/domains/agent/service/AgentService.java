package com.agent.monito.domains.agent.service;

import com.agent.monito.domains.agent.dto.response.AgentStatusResponseDTO;

public interface AgentService {
    AgentStatusResponseDTO getHealthStatus();
}
