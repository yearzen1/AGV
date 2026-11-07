package com.agv.backend.common;

/**
 * 业务异常：由 {@link GlobalExceptionHandler} 统一转成 {@link ApiResponse}。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(4000, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
