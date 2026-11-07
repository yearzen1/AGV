package com.agv.backend.common;

import com.agv.algorithm.model.ValidationError;

import java.util.List;

/**
 * 路网校验未通过。携带逐条错误，前端据此把节点/路段标红。
 */
public class RoadNetInvalidException extends RuntimeException {

    private final List<ValidationError> errors;

    public RoadNetInvalidException(List<ValidationError> errors) {
        super("路网校验未通过");
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}
