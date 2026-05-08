package com.incidentmgmt.repository;

import com.incidentmgmt.entity.AiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    List<AiCallLog> findTop20ByOrderByCreatedAtDesc();

    long countBySuccessTrue();
}
