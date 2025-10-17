package com.agent.monito.global.exception;

import lombok.Getter;

@Getter
public class InvalidFileException extends RuntimeException {
    private final ExceptionMessage exceptionMessage;

    public InvalidFileException(ExceptionMessage exceptionMessage) {
        super(exceptionMessage.getMessage());
        this.exceptionMessage = exceptionMessage;
    }
}
