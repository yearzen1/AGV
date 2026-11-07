package com.agv.backend.repository;

import com.agv.backend.entity.WarehouseMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WarehouseMapRepository extends JpaRepository<WarehouseMap, Long> {

    List<WarehouseMap> findAllByOrderByIdAsc();

    List<WarehouseMap> findByIsTemplateTrueOrderByIdAsc();
}
