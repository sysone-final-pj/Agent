/**
 * 컨테이너 로그 파싱 유틸리티 클래스
 * Docker 로그의 타임스탬프와 StreamType을 파싱
 */
package com.agent.monito.global.util;

import com.github.dockerjava.api.model.StreamType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LogParser {

    // Docker 타임스탬프 정규식 패턴 (2025-10-29T00:47:41.599847751Z)
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z)\\s*(.*)$");

    /**
     * Docker 로그 원시 문자열에서 타임스탬프와 메시지 분리
     *
     * @param rawLog Docker 로그 원시 문자열
     * @return 파싱된 로그 (timestamp, message)
     */
    public ParsedLog parseTimestamp(String rawLog) {
        if (rawLog == null || rawLog.isEmpty()) {
            return null;
        }

        Matcher matcher = TIMESTAMP_PATTERN.matcher(rawLog);

        if (matcher.matches()) {
            String timestamp = matcher.group(1);  // 2025-10-29T00:47:41.599847751Z
            String message = matcher.group(2);    // 타임스탬프 제거된 메시지
            return new ParsedLog(timestamp, message);
        } else {
            // 타임스탬프가 없는 경우 (예외적인 상황)
            log.warn("Log without timestamp detected: {}",
                    rawLog.substring(0, Math.min(50, rawLog.length())));
            return new ParsedLog(null, rawLog);
        }
    }

    /**
     * StreamType을 문자열로 변환
     *
     * @param streamType Docker StreamType
     * @return "stdout", "stderr", "stdin", "raw"
     */
    public String parseSource(StreamType streamType) {
        if (streamType == null) {
            return "raw";
        }

        switch (streamType) {
            case STDOUT:
                return "stdout";
            case STDERR:
                return "stderr";
            case STDIN:
                return "stdin";
            case RAW:
            default:
                return "raw";
        }
    }

    /**
     * 파싱된 로그 데이터 클래스
     */
    @Getter
    @AllArgsConstructor
    public static class ParsedLog {
        private final String timestamp;
        private final String message;
    }
}