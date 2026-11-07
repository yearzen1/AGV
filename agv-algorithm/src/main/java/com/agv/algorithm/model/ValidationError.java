package com.agv.algorithm.model;

/**
 * 单条路网校验错误。前端据此把对应节点/路段标红。
 *
 * @param code    机器可读错误码
 * @param message 中文提示，直接展示给用户
 * @param nodeId  关联节点 ID，可为 null
 * @param edgeId  关联路段 ID，可为 null
 */
public record ValidationError(String code, String message, String nodeId, String edgeId) {

    public static ValidationError of(String code, String message) {
        return new ValidationError(code, message, null, null);
    }

    public static ValidationError node(String code, String message, String nodeId) {
        return new ValidationError(code, message, nodeId, null);
    }

    public static ValidationError edge(String code, String message, String edgeId) {
        return new ValidationError(code, message, null, edgeId);
    }
}
