package com.agv.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 数字字典。本期落地 algorithm（调度算法类型）与 metric（评价指标）两类。
 */
@Entity
@Table(name = "sys_dict")
public class SysDict extends BaseEntity {

    @Column(name = "dict_type", nullable = false, length = 32)
    private String dictType;

    @Column(name = "dict_code", nullable = false, length = 64)
    private String dictCode;

    @Column(name = "dict_value", nullable = false, length = 128)
    private String dictValue;

    @Column(nullable = false)
    private Integer sort = 0;

    @Column(nullable = false)
    private Integer status = 1;

    public SysDict() {
    }

    public SysDict(String dictType, String dictCode, String dictValue, Integer sort) {
        this.dictType = dictType;
        this.dictCode = dictCode;
        this.dictValue = dictValue;
        this.sort = sort;
    }

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getDictCode() {
        return dictCode;
    }

    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }

    public String getDictValue() {
        return dictValue;
    }

    public void setDictValue(String dictValue) {
        this.dictValue = dictValue;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
