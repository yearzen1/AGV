package com.agv.backend.repository;

import com.agv.backend.entity.ExperimentMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExperimentMetricRepository extends JpaRepository<ExperimentMetric, Long> {

    Optional<ExperimentMetric> findByExperimentId(Long experimentId);

    /** 历史列表一次取回本页指标，避免每条实验各查一次。 */
    List<ExperimentMetric> findByExperimentIdIn(Collection<Long> experimentIds);
}
