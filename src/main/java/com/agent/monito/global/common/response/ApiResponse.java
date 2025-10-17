package com.agent.monito.global.common.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class ApiResponse<T> {

    public static final String EMPTY = "";
    public static final int OK_CODE = 200;
    public static final int CREATED_CODE = 201;
    public static final String DEFAULT_MESSAGE = "요청이 성공적으로 처리되었습니다.";

    private int statusCode;
    private String message;
    private T data;

    public static <T> ApiResponse<String> ok() {
        return new ApiResponse<>(OK_CODE, DEFAULT_MESSAGE, null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(OK_CODE, DEFAULT_MESSAGE, data);
    }

    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(OK_CODE, message, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(OK_CODE, message, data);
    }

    public static <T> ApiResponse<T> ok(int statusCode, T data, String message) {
        return new ApiResponse<>(statusCode, message, data);
    }

    public static ApiResponse<String> okWithoutData(int statusCode, String message) {
        return new ApiResponse<>(statusCode, message, EMPTY);
    }

    public static ApiResponse<String> okWithoutData() {
        return new ApiResponse<>(OK_CODE, DEFAULT_MESSAGE, EMPTY);
    }

    public static ApiResponse<Void> created() {
        return new ApiResponse<>(CREATED_CODE, DEFAULT_MESSAGE, null);
    }

    public static ApiResponse<Void> created(String message) {
        return new ApiResponse<>(CREATED_CODE, message, null);
    }

    public static <T> ApiResponse<T> created(T data, String message) { return new ApiResponse<>(CREATED_CODE, message, data); }

    private ApiResponse(int statusCode, String message, T data) {
        this.statusCode = statusCode;
        this.message = message;
        this.data = data;
    }
}
