package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.RiskAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskAlertRepository extends JpaRepository<RiskAlert, Long> {
    List<RiskAlert> findByStatus(RiskAlert.RiskStatus status);
    List<RiskAlert> findBySeverityLevel(RiskAlert.SeverityLevel severityLevel);
    List<RiskAlert> findByOrderByCreatedAtDesc();
}
