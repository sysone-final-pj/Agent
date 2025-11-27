/**
 * Docker Frame을 로그 DTO로 변환하는 Mapper 클래스
 * 원시 Frame 데이터를 ContainerLogEntryResponseDTO로 매핑
 */
package com.agent.monito.global.mapper;

import com.agent.monito.domains.container.dto.response.ContainerLogEntryResponseDTO;
import com.agent.monito.global.util.LogParser;
import com.github.dockerjava.api.model.Frame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
/**
 작성자: 백승준
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerLogsMapper {

    private final LogParser logParser;

    /**
     * Docker Frame을 ContainerLogEntryResponseDTO로 변환
     *
     * @param frame Docker 로그 프레임
     * @return ContainerLogEntryResponseDTO (파싱 실패 시 null)
     */
    public ContainerLogEntryResponseDTO toLogEntryDTO(Frame frame) {
        try {
            String rawLog = new String(frame.getPayload(), StandardCharsets.UTF_8).trim();

            // 빈 로그 무시
            if (rawLog.isEmpty()) {
                return null;
            }

            // 타임스탬프 파싱
            LogParser.ParsedLog parsedLog = logParser.parseTimestamp(rawLog);
            if (parsedLog == null) {
                return null;
            }

            // source 파싱 (stdout, stderr, raw)
            String source = logParser.parseSource(frame.getStreamType()).toUpperCase();

            // UTC 타임스탬프를 KST로 변환 (이미 KST면 그대로 반환)
            String kstTimestamp = logParser.convertToKST(parsedLog.getTimestamp());

            return ContainerLogEntryResponseDTO.builder()
                    .message(parsedLog.getMessage())
                    .source(source)
                    .timestamp(kstTimestamp)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse log frame: {}", e.getMessage());
            return null;
        }
    }
}