package com.agv.algorithm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 路网校验结果。{@code errors} 拦截保存，{@code warnings} 只提示。
 */
public record ValidationResult(List<ValidationError> errors, List<ValidationError> warnings) {

    public ValidationResult {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public ValidationResult(List<ValidationError> errors) {
        this(errors, List.of());
    }

    public static ValidationResult ok() {
        return new ValidationResult(List.of(), List.of());
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    /** 错误与警告合并，供「边改边验」接口一次性返回。 */
    public List<ValidationError> all() {
        List<ValidationError> merged = new ArrayList<>(errors);
        merged.addAll(warnings);
        return List.copyOf(merged);
    }
}
