/**
 * Agent 연결 시 전송할 호스트(VM) 정보를 담는 DTO
 * 연결 성공 시 1회 전송, 이후 1시간마다 재전송
 */
package com.agent.monito.domains.agent.dto.response;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentInfoResponseDTO {
    private String agentKey;
    private Long hostTotalMemory;   // 호스트의 전체 물리 메모리 (bytes)
    private Integer hostCpuCores;   // 호스트의 CPU 코어 수
    private Long hostTotalDisk;     // 호스트의 전체 디스크 용량 (bytes)
}
