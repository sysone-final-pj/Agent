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
     * 로그 발생 시각 (Docker 타임스탬프)
     * ISO 8601 형식: 2025-10-29T00:47:41.599847751Z
     */
    private String timestamp;
}