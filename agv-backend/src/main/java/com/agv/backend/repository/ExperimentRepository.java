package com.agv.backend.repository;

import com.agv.backend.entity.Experiment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperimentRepository extends JpaRepository<Experiment, Long> {

    List<Experiment> findByCreateUserOrderByIdDesc(Long createUser);

    List<Experiment> findByMapIdAndTaskSetIdOrderByIdDesc(Long mapId, Long taskSetId);

    List<Experiment> findByStatus(String status);
}
