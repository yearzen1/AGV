package com.agv.backend.common;

import com.agv.algorithm.model.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(statusOf(ex.getCode()))
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
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

    /** 请求体不是合法 JSON、字段类型不对：属于调用方错误，不应变成 500。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(4000, "请求体格式错误，请检查 JSON 字段与类型"));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadParameter(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(4000, "请求参数缺失或类型错误"));
    }

    /** 唯一键、外键等数据库约束冲突：语义上是 409，而不是 500。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataConflict(DataIntegrityViolationException ex) {
        log.warn("数据库约束冲突", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(4090, "数据冲突：记录可能重复，或仍被其他数据引用"));
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

    /**
     * 兜底：不把异常消息返回给调用方，只给一个可检索的请求编号。
     * 堆栈写服务端日志，调用方可凭 X-Request-Id 让管理员定位。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        log.error("未处理的异常 traceId={}", traceId, ex);
        String message = traceId == null
                ? "服务器内部错误"
                : "服务器内部错误，请将请求编号 " + traceId + " 反馈给管理员";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(5000, message));
    }

    /**
     * 业务码是四位十进制：前三位就是目标 HTTP 状态（4012 → 401，4040 → 404）。
     * 没有对应状态时按 400 处理。
     */
    static HttpStatus statusOf(int code) {
        HttpStatus status = HttpStatus.resolve(code / 10);
        return status != null && status.isError() ? status : HttpStatus.BAD_REQUEST;
    }
}
