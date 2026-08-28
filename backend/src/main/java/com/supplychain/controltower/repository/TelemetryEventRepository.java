package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.TelemetryEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelemetryEventRepository extends JpaRepository<TelemetryEventEntity, Long> {

    List<TelemetryEventEntity> findByOrderByCreatedAtDesc(Pageable pageable);

    List<TelemetryEventEntity> findBySeverityInOrderByCreatedAtDesc(List<String> severities, Pageable pageable);

    @Query("SELECT e FROM TelemetryEventEntity e WHERE e.severity IN ('WARNING', 'ERROR', 'CRITICAL') ORDER BY e.createdAt DESC")
    List<TelemetryEventEntity> findActiveAlerts(Pageable pageable);
}
