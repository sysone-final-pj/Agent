/**
 * Agent가 설치된 호스트(VM)의 디스크 정보를 담는 DTO
 */
package com.agent.monito.domains.agent.dto.response;

import lombok.*;
/**
 작성자: 백승준
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HostDiskInfoResponseDTO {
    private Long totalDisk;         // 호스트의 전체 디스크 용량 (bytes)
    private Long availableDisk;     // 호스트의 사용 가능한 디스크 공간 (bytes)
    private Long usedDisk;          // 호스트의 사용 중인 디스크 공간 (bytes)
}