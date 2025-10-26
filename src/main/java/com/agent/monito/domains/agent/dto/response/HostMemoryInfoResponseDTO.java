/**
 * Agent가 설치된 호스트(VM)의 메모리 정보를 담는 DTO
 */
package com.agent.monito.domains.agent.dto.response;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HostMemoryInfoResponseDTO {
    private Long totalMemory;       // 호스트의 전체 물리 메모리 (bytes)
    private Long availableMemory;   // 호스트의 사용 가능한 메모리 (bytes)
    private Long usedMemory;        // 호스트의 사용 중인 메모리 (bytes)
}
