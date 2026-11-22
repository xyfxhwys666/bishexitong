package com.xyfxhwys.faceprivacy.task;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingTaskRepository extends JpaRepository<ProcessingTask, Long> {
    List<ProcessingTask> findTop20ByOrderByCreatedAtDesc();
}
