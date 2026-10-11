package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.RiskAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiskAlertRepository extends JpaRepository<RiskAlert, Long> {
    List<RiskAlert> findByCompanyId(Long companyId);
    Optional<RiskAlert> findByCompanyIdAndId(Long companyId, Long id);
    List<RiskAlert> findByCompanyIdAndStatus(Long companyId, RiskAlert.RiskStatus status);
    List<RiskAlert> findByCompanyIdAndSeverityLevel(Long companyId, RiskAlert.SeverityLevel severityLevel);
    List<RiskAlert> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    long countByCompanyId(Long companyId);
    long countByCompanyIdAndStatus(Long companyId, RiskAlert.RiskStatus status);

    List<RiskAlert> findByStatus(RiskAlert.RiskStatus status);
    List<RiskAlert> findBySeverityLevel(RiskAlert.SeverityLevel severityLevel);
    List<RiskAlert> findByOrderByCreatedAtDesc();
}
