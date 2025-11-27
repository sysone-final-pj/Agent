package com.agent.monito.global.exception;

import lombok.Getter;
/**
 작성자: 백승준
 */
@Getter
public class InvalidFileException extends RuntimeException {
    private final ExceptionMessage exceptionMessage;

    public InvalidFileException(ExceptionMessage exceptionMessage) {
        super(exceptionMessage.getMessage());
        this.exceptionMessage = exceptionMessage;
    }
}
