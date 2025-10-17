package com.agent.monito.global.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException {
    private final ExceptionMessage exceptionMessage;

    public NotFoundException(ExceptionMessage exceptionMessage) {
        super(exceptionMessage.getMessage());
        this.exceptionMessage = exceptionMessage;
    }
}