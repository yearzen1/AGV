package com.agv.backend.repository;

import com.agv.backend.entity.RoadNetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 路网持久化仓储。对应表 {@code roadnet}，领域名是 RoadNet。
 */
public interface RoadNetRepository extends JpaRepository<RoadNetEntity, Long> {

    List<RoadNetEntity> findAllByOrderByIdAsc();

    List<RoadNetEntity> findByIsTemplateTrueOrderByIdAsc();
}
