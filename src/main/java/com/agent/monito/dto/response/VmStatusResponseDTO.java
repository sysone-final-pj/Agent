/**
 * Agent가 설치된 VM(호스트)의 상태 정보를 담는 DTO.
 * CPU, 메모리, 디스크 등 시스템 레벨의 자원 상태를 표현함.
 */
package com.agent.monito.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VmStatusResponseDTO {
    private String status;
    private String message;
}