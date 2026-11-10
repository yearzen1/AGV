package com.agv.backend.repository;

import com.agv.backend.entity.Experiment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperimentRepository extends JpaRepository<Experiment, Long> {

    List<Experiment> findByCreateUserOrderByIdDesc(Long createUser);

    Page<Experiment> findByCreateUserOrderByIdDesc(Long createUser, Pageable pageable);

    List<Experiment> findByRoadnetIdAndTaskSetIdOrderByIdDesc(Long roadnetId, Long taskSetId);

    List<Experiment> findByStatus(String status);
}
