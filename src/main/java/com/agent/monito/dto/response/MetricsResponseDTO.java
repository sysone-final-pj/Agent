/**
 * 단일 컨테이너의 메트릭 데이터(CPU, 메모리, 네트워크, 디스크)를 담는 응답 객체
 * MetricsController에서 JSON 형태로 직렬화되어 반환됨.
 */
package com.agent.monito.dto.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MetricsResponseDTO {
    private String containerName;
    private double cpuUsage;
    private double memoryUsage;
    private double networkIO;
    private double diskIO;
}