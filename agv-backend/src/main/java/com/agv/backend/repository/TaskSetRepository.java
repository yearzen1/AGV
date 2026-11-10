package com.agv.backend.repository;

import com.agv.backend.entity.TaskSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskSetRepository extends JpaRepository<TaskSet, Long> {

    List<TaskSet> findAllByOrderByIdDesc();

    List<TaskSet> findByCreateUserOrderByIdDesc(Long createUser);

    List<TaskSet> findByMapIdOrderByIdDesc(Long mapId);

    List<TaskSet> findByMapIdIsNull();

    long countByMapId(Long mapId);
}
