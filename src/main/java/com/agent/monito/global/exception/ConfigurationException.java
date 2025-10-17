package com.agent.monito.global.exception;

import lombok.Getter;

@Getter
public class ConfigurationException extends RuntimeException {
    private final ExceptionMessage exceptionMessage;

    public ConfigurationException(ExceptionMessage exceptionMessage) {
        super(exceptionMessage.getMessage());
        this.exceptionMessage = exceptionMessage;
    }
}
