package com.agent.monito.domains.agent.service;

import com.agent.monito.domains.agent.dto.response.AgentStatusResponseDTO;
/**
 작성자: 백승준
 */
public interface AgentService {
    AgentStatusResponseDTO getHealthStatus();
}
