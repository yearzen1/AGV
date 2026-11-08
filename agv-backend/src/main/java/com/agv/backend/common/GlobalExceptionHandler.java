package com.agv.backend.common;

import com.agv.algorithm.model.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    /** 路网校验失败：把带 nodeId/edgeId 的错误明细返回给前端定位高亮。 */
    @ExceptionHandler(RoadNetInvalidException.class)
    public ResponseEntity<ApiResponse<List<ValidationError>>> handleInvalidRoadNet(RoadNetInvalidException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(4001, "路网校验未通过", ex.getErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .collect(Collectors.joining("；"));
        return ResponseEntity.badRequest().body(ApiResponse.fail(4000, message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(4030, "没有权限执行该操作"));
    }

    /** 路径写错时给 404，而不是让通用处理器把它变成 500。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        String path = "/" + ex.getResourcePath();
        log.warn("接口不存在：{}", path);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(4040, "接口不存在：" + path));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        log.error("未处理的异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(5000, "服务器内部错误：" + ex.getMessage()));
    }
}
