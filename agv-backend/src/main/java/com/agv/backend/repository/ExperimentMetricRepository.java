package com.agv.backend.repository;

import com.agv.backend.entity.ExperimentMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExperimentMetricRepository extends JpaRepository<ExperimentMetric, Long> {

    Optional<ExperimentMetric> findByExperimentId(Long experimentId);
}
