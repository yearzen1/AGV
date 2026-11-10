package com.agv.backend.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 分页响应：{@code { total, page, size, records }}。
 * 页码从 0 开始，与 Spring Data 的 {@link Page} 一致。
 */
public record PageResponse<T>(long total, int page, int size, List<T> records) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getTotalElements(), page.getNumber(), page.getSize(), page.getContent());
    }
}
