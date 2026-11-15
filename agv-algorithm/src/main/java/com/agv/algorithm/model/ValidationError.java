package com.agv.algorithm.model;

/**
 * 单条路网校验结论。前端据此把对应节点/路段标红（错误）或标黄（警告）。
 *
 * @param code     机器可读错误码
 * @param message  中文提示，直接展示给用户
 * @param nodeId   关联节点 ID，可为 null
 * @param edgeId   关联路段 ID，可为 null
 * @param severity 严重级别：{@code ERROR} 拦截保存，{@code WARNING} 只提示
 */
public record ValidationError(String code, String message, String nodeId, String edgeId,
                              Severity severity) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public static ValidationError of(String code, String message) {
        return new ValidationError(code, message, null, null, Severity.ERROR);
    }

    public static ValidationError node(String code, String message, String nodeId) {
        return new ValidationError(code, message, nodeId, null, Severity.ERROR);
    }

    public static ValidationError edge(String code, String message, String edgeId) {
        return new ValidationError(code, message, null, edgeId, Severity.ERROR);
    }

    public static ValidationError warningNode(String code, String message, String nodeId) {
        return new ValidationError(code, message, nodeId, null, Severity.WARNING);
    }
}
