package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.CsvImportHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CsvImportHistoryRepository extends JpaRepository<CsvImportHistory, Long> {

    @EntityGraph(attributePaths = {"company"})
    List<CsvImportHistory> findByCompanyIdOrderByStartedAtDesc(Long companyId);

    boolean existsByCompanyIdAndFileHashAndStatus(Long companyId, String fileHash, CsvImportHistory.ImportStatus status);

    Optional<CsvImportHistory> findTopByCompanyIdAndFileHashAndStatusOrderByStartedAtDesc(
            Long companyId, String fileHash, CsvImportHistory.ImportStatus status);
}
