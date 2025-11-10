/**
 * Agent가 설치된 호스트(VM)의 메모리 정보를 담는 DTO
 * Docker가 실행되는 환경의 메모리 정보 (정적 + 동적)
 */
package com.agent.monito.domains.agent.dto.response;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HostMemoryInfoResponseDTO {
    private Long totalMemory;       // Docker 호스트의 전체 물리 메모리 (bytes) - Docker API
    private Long availableMemory;   // 호스트의 사용 가능한 메모리 (bytes) - osBean
    private Long usedMemory;        // 호스트의 사용 중인 메모리 (bytes) - 계산값
}
