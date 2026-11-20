package com.agv.backend.repository;

import com.agv.backend.entity.SimulationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimulationEventRepository extends JpaRepository<SimulationEventEntity, Long> {

    List<SimulationEventEntity> findByExperimentIdOrderBySeqNoAsc(Long experimentId);

    long countByExperimentId(Long experimentId);
}
