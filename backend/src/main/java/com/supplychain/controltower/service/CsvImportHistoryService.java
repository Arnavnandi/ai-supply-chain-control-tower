package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.CsvImportHistoryDto;
import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.CsvImportHistory;
import com.supplychain.controltower.exception.ResourceNotFoundException;
import com.supplychain.controltower.repository.CompanyRepository;
import com.supplychain.controltower.repository.CsvImportHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportHistoryService {

    private final CsvImportHistoryRepository historyRepository;
    private final CompanyRepository companyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordImportStart(Long companyId, Long userId, String username, String filename,
                                  String entityType, Long fileSizeBytes, String fileHash) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + companyId));

        CsvImportHistory history = CsvImportHistory.builder()
                .company(company)
                .userId(userId)
                .username(username != null && !username.isBlank() ? username : "system")
                .filename(filename != null && !filename.isBlank() ? filename : "unknown.csv")
                .entityType(entityType != null ? entityType.toLowerCase() : "unknown")
                .status(CsvImportHistory.ImportStatus.IN_PROGRESS)
                .totalRows(0)
                .recordsImported(0)
                .recordsFailed(0)
                .fileSizeBytes(fileSizeBytes)
                .fileHash(fileHash)
                .startedAt(LocalDateTime.now())
                .build();

        CsvImportHistory saved = historyRepository.save(history);
        log.info("[CSV IMPORT HISTORY START] Created record id={}, companyId={}, filename='{}', entity='{}', hash='{}'",
                saved.getId(), companyId, filename, entityType, fileHash);
        return saved.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordImportSuccess(Long historyId, CsvImportService.ImportResult result) {
        if (historyId == null) return;
        historyRepository.findById(historyId).ifPresent(history -> {
            history.setStatus(CsvImportHistory.ImportStatus.IMPORTED);
            history.setTotalRows(result != null ? result.getTotalRowsProcessed() : 0);
            history.setRecordsImported(result != null ? result.getRecordsImported() : 0);
            history.setRecordsFailed(result != null ? result.getRecordsFailed() : 0);
            history.setCompletedAt(LocalDateTime.now());
            historyRepository.save(history);
            log.info("[CSV IMPORT HISTORY SUCCESS] Updated record id={}, imported={}/{}",
                    historyId, history.getRecordsImported(), history.getTotalRows());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordImportFailure(Long historyId, int totalRowsProcessed, int recordsFailed,
                                    List<String> errors, String failureMessage) {
        if (historyId == null) return;
        historyRepository.findById(historyId).ifPresent(history -> {
            history.setStatus(CsvImportHistory.ImportStatus.FAILED);
            history.setTotalRows(totalRowsProcessed);
            history.setRecordsFailed(recordsFailed > 0 ? recordsFailed : Math.max(1, errors != null ? errors.size() : 1));
            history.setCompletedAt(LocalDateTime.now());

            StringBuilder summary = new StringBuilder();
            if (failureMessage != null && !failureMessage.isBlank()) {
                summary.append(failureMessage).append("\n");
            }
            if (errors != null && !errors.isEmpty()) {
                for (int i = 0; i < Math.min(errors.size(), 20); i++) {
                    summary.append(errors.get(i)).append("\n");
                }
                if (errors.size() > 20) {
                    summary.append("... and ").append(errors.size() - 20).append(" more errors");
                }
            }
            String finalSummary = summary.toString().trim();
            if (finalSummary.length() > 3800) {
                finalSummary = finalSummary.substring(0, 3800) + "... [truncated]";
            }
            history.setErrorSummary(finalSummary);

            historyRepository.save(history);
            log.warn("[CSV IMPORT HISTORY FAILED] Updated record id={}, totalRows={}, failed={}",
                    historyId, totalRowsProcessed, history.getRecordsFailed());
        });
    }

    @Transactional(readOnly = true)
    public List<CsvImportHistoryDto> getTenantImportHistory(Long companyId) {
        List<CsvImportHistory> historyList = historyRepository.findByCompanyIdOrderByStartedAtDesc(companyId);

        // Track seen hashes in chronological order so only subsequent uploads are flagged as duplicate
        Set<String> seenImportedHashes = new HashSet<>();
        Set<Long> duplicateRecordIds = new HashSet<>();
        List<CsvImportHistory> chronological = new ArrayList<>(historyList);
        Collections.reverse(chronological);

        for (CsvImportHistory h : chronological) {
            if (h.getFileHash() != null && h.getStatus() == CsvImportHistory.ImportStatus.IMPORTED) {
                if (seenImportedHashes.contains(h.getFileHash())) {
                    duplicateRecordIds.add(h.getId());
                } else {
                    seenImportedHashes.add(h.getFileHash());
                }
            }
        }

        return historyList.stream().map(h -> {
            boolean isDuplicate = duplicateRecordIds.contains(h.getId());
            List<String> errorList = new ArrayList<>();
            if (h.getErrorSummary() != null && !h.getErrorSummary().isBlank()) {
                errorList = Arrays.asList(h.getErrorSummary().split("\n"));
            }

            return CsvImportHistoryDto.builder()
                    .id(h.getId())
                    .companyId(h.getCompany() != null ? h.getCompany().getId() : companyId)
                    .companyCode(h.getCompany() != null ? h.getCompany().getCode() : null)
                    .userId(h.getUserId())
                    .username(h.getUsername())
                    .filename(h.getFilename())
                    .entityType(h.getEntityType())
                    .status(h.getStatus().name())
                    .totalRows(h.getTotalRows())
                    .recordsImported(h.getRecordsImported())
                    .recordsFailed(h.getRecordsFailed())
                    .errorSummary(h.getErrorSummary())
                    .errorList(errorList)
                    .fileSizeBytes(h.getFileSizeBytes())
                    .fileHash(h.getFileHash())
                    .duplicateFile(isDuplicate)
                    .startedAt(h.getStartedAt())
                    .completedAt(h.getCompletedAt())
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public boolean isDuplicateFile(Long companyId, String fileHash) {
        if (fileHash == null || fileHash.isBlank()) return false;
        return historyRepository.existsByCompanyIdAndFileHashAndStatus(
                companyId, fileHash, CsvImportHistory.ImportStatus.IMPORTED);
    }

    public String computeSha256(byte[] data) {
        if (data == null || data.length == 0) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            log.error("SHA-256 algorithm not available", ex);
            return null;
        }
    }
}
