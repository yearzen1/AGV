package com.agv.backend.repository;

import com.agv.backend.entity.SysDict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysDictRepository extends JpaRepository<SysDict, Long> {

    List<SysDict> findByDictTypeOrderBySortAsc(String dictType);

    List<SysDict> findByDictTypeAndStatusOrderBySortAsc(String dictType, Integer status);

    boolean existsByDictTypeAndDictCode(String dictType, String dictCode);
}
