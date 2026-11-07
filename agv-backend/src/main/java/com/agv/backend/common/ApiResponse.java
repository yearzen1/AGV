package com.agv.backend.common;

/**
 * 统一响应体：{@code {code, message, data}}。
 * code = 0 表示成功，非 0 为业务错误码（与 HTTP 状态码解耦，方便前端统一提示）。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
