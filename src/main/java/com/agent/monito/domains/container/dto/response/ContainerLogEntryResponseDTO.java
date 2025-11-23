/**
 * 컨테이너 로그 항목 데이터 (원시 데이터만 포함)
 */
package com.agent.monito.domains.container.dto.response;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContainerLogEntryResponseDTO {

    /**
     * 타임스탬프가 제거된 순수 로그 메시지
     */
    private String message;

    /**
     * 로그 출력 소스 (stdout, stderr, raw)
     */
    private String source;

    /**
     * 로그 발생 시각 (KST LocalDateTime)
     * ISO 8601 LocalDateTime 형식: 2025-11-24T09:47:41.599847751
     * UTC 타임스탬프를 KST로 변환 후 타임존 정보 제거
     */
    private String timestamp;
}