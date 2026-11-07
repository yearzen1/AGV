package com.agv.algorithm.model;

import java.util.List;

/**
 * 路网校验结果。
 */
public record ValidationResult(List<ValidationError> errors) {

    public ValidationResult {
        errors = List.copyOf(errors);
    }

    public static ValidationResult ok() {
        return new ValidationResult(List.of());
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    public List<ValidationError> errors() {
        return errors;
    }
}
