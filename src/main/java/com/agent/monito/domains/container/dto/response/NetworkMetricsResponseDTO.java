/**
 * Network 관련 메트릭 데이터 (원시 데이터만 포함)
 */
package com.agent.monito.domains.container.dto.response;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NetworkMetricsResponseDTO {
    private Long rxBytes;                   // 수신 바이트 (누적값)
    private Long txBytes;                   // 송신 바이트 (누적값)
    private Long rxPackets;                 // 수신 패킷 수 (누적값)
    private Long txPackets;                 // 송신 패킷 수 (누적값)
    private Long rxErrors;                  // 수신 에러 수 (누적값)
    private Long txErrors;                  // 송신 에러 수 (누적값)
    private Long rxDropped;                 // 수신 드롭 수 (누적값)
    private Long txDropped;                 // 송신 드롭 수 (누적값)
}