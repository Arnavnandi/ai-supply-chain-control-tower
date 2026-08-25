package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByOrderByTimestampDesc();
    List<AuditLog> findByUserId(Long userId);
}
