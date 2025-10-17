package com.agent.monito.service;

import com.agent.monito.dto.response.VmStatusResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthService {

    public VmStatusResponseDTO getHealthStatus() {
        return new VmStatusResponseDTO("OK", "Agent is running normally");
    }
}