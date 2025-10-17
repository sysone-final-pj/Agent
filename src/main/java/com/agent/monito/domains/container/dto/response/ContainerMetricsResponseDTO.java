/**
 * 단일 컨테이너의 메트릭 데이터(CPU, 메모리, 네트워크, 디스크)를 담는 응답 객체
 * MetricsController에서 JSON 형태로 직렬화되어 반환됨.
 */
package com.agent.monito.domains.container.dto.response;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContainerMetricsResponseDTO {
    private String containerName;
    private double cpuUsage;
    private double memoryUsage;
    private double networkIO;
    private double diskIO;
}