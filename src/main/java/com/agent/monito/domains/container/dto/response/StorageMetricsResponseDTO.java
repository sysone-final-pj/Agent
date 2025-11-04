/**
 * 컨테이너의 Storage 관련 메트릭 데이터를 담는 DTO
 * - Container Size (writable layer, root fs)
 * - Image Size
 */
package com.agent.monito.domains.container.dto.response;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StorageMetricsResponseDTO {

    // Container Size
    private Long sizeRw;        // Writable layer 크기 (컨테이너가 생성한 데이터) - bytes
    private Long sizeRootFs;    // 가상 크기 (이미지 + writable layer) - bytes

    // Storage Limit
    private Long storageLimit;  // 컨테이너 저장 공간 제한 (--storage-opt size) - bytes, 0이면 제한 없음

    // Image Size
    private Long imageSize;     // 이미지 크기 - bytes
    private String imageName;   // 이미지 이름 (참고용)
}