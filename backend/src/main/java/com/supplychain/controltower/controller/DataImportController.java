package com.supplychain.controltower.controller;

import com.supplychain.controltower.dto.domain.CsvImportHistoryDto;
import com.supplychain.controltower.security.TenantContext;
import com.supplychain.controltower.security.UserPrincipal;
import com.supplychain.controltower.service.CsvImportHistoryService;
import com.supplychain.controltower.service.CsvImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;

@RestController
@RequestMapping("/api/data/import")
@RequiredArgsConstructor
@Slf4j
public class DataImportController {

    private final CsvImportService csvImportService;
    private final CsvImportHistoryService csvImportHistoryService;

    @GetMapping("/history")
    public ResponseEntity<List<CsvImportHistoryDto>> getImportHistory(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserPrincipal currentUser) {
        Long companyId = currentUser != null && currentUser.getCompanyId() != null
                ? currentUser.getCompanyId()
                : TenantContext.getRequiredCompanyId();
        List<CsvImportHistoryDto> history = csvImportHistoryService.getTenantImportHistory(companyId);
        return ResponseEntity.ok(history);
    }

    public ResponseEntity<List<CsvImportHistoryDto>> getImportHistory() {
        return getImportHistory(resolveCurrentPrincipal());
    }

    @PostMapping("/sample-dataset")
    public ResponseEntity<CsvImportService.ImportResult> importSampleDataset() {
        return importSampleDataset(resolveCurrentPrincipal());
    }

    public ResponseEntity<CsvImportService.ImportResult> importSampleDataset(UserPrincipal principal) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Long userId = principal != null ? principal.getId() : null;
        String username = principal != null ? principal.getUsername() : "system";

        log.info("[REST API] Triggered 1-click sample dataset import for companyId: {}", companyId);
        Long historyId = csvImportHistoryService.recordImportStart(
                companyId, userId, username, "sample-dataset.bundle", "sample-dataset", 0L, null);

        try {
            CsvImportService.ImportResult result = csvImportService.importSampleDataset();
            if (!result.isSuccess()) {
                csvImportHistoryService.recordImportFailure(
                        historyId, result.getTotalRowsProcessed(), result.getRecordsFailed(),
                        result.getErrors(), result.getMessage());
                return ResponseEntity.badRequest().body(result);
            }
            csvImportHistoryService.recordImportSuccess(historyId, result);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            log.error("[REST API SAMPLE IMPORT FAIL] Error: {}", ex.getMessage(), ex);
            csvImportHistoryService.recordImportFailure(
                    historyId, 0, 1, List.of(ex.getMessage()), "Sample dataset import failed: " + ex.getMessage());
            throw ex;
        }
    }

    @PostMapping("/file")
    public ResponseEntity<?> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "entityType", defaultValue = "products") String entityType) {
        return importFile(file, entityType, resolveCurrentPrincipal());
    }

    public ResponseEntity<?> importFile(
            MultipartFile file,
            String entityType,
            UserPrincipal principal) {

        Long companyId = TenantContext.getRequiredCompanyId();
        Long userId = principal != null ? principal.getId() : null;
        String username = principal != null ? principal.getUsername() : "system";
        String filename = (file != null && file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
                ? file.getOriginalFilename() : "uploaded.csv";
        long fileSize = file != null ? file.getSize() : 0L;

        log.info("[REST API] File upload received for companyId {}: entityType '{}', filename '{}', size {} bytes",
                companyId, entityType, filename, fileSize);

        if (file == null || file.isEmpty()) {
            Long historyId = csvImportHistoryService.recordImportStart(
                    companyId, userId, username, filename, entityType, 0L, null);
            csvImportHistoryService.recordImportFailure(
                    historyId, 0, 1, List.of("Uploaded CSV file is empty (0 bytes)."), "Uploaded CSV file is empty.");

            return ResponseEntity.badRequest().body(CsvImportService.ImportResult.builder()
                    .success(false)
                    .entityType(entityType)
                    .totalRowsProcessed(0)
                    .recordsImported(0)
                    .recordsFailed(1)
                    .recordsRejected(0)
                    .errors(List.of("Uploaded CSV file is empty (0 bytes)."))
                    .message("Uploaded CSV file is empty.")
                    .build());
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception ex) {
            log.error("[REST API] Failed to read multipart bytes: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(CsvImportService.ImportResult.builder()
                    .success(false)
                    .entityType(entityType)
                    .totalRowsProcessed(0)
                    .recordsImported(0)
                    .recordsFailed(1)
                    .errors(List.of("Failed to read uploaded file: " + ex.getMessage()))
                    .message("Failed to read uploaded file.")
                    .build());
        }

        String fileHash = csvImportHistoryService.computeSha256(fileBytes);
        boolean isDuplicate = csvImportHistoryService.isDuplicateFile(companyId, fileHash);

        Long historyId = csvImportHistoryService.recordImportStart(
                companyId, userId, username, filename, entityType, fileSize, fileHash);

        try {
            ByteArrayInputStream is = new ByteArrayInputStream(fileBytes);
            CsvImportService.ImportResult result = csvImportService.importSingleEntityFile(entityType, is);

            if (!result.isSuccess()) {
                csvImportHistoryService.recordImportFailure(
                        historyId, result.getTotalRowsProcessed(), result.getRecordsFailed(),
                        result.getErrors(), result.getMessage());
                return ResponseEntity.badRequest().body(result);
            }

            if (isDuplicate) {
                result.setDuplicateDetected(true);
                result.setDuplicateWarning("Duplicate file warning: An identical CSV file was previously imported for this tenant. Existing records were updated in-place via business keys.");
            }

            csvImportHistoryService.recordImportSuccess(historyId, result);
            return ResponseEntity.ok(result);

        } catch (com.supplychain.controltower.exception.TenantContextMissingException ex) {
            csvImportHistoryService.recordImportFailure(
                    historyId, 0, 1, List.of(ex.getMessage()), "Tenant context missing: " + ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("[REST API IMPORT FAIL] Error processing CSV upload: {}", ex.getMessage(), ex);
            csvImportHistoryService.recordImportFailure(
                    historyId, 0, 1, List.of(ex.getMessage()), "CSV import failed: " + ex.getMessage());

            return ResponseEntity.badRequest().body(CsvImportService.ImportResult.builder()
                    .success(false)
                    .entityType(entityType)
                    .totalRowsProcessed(0)
                    .recordsImported(0)
                    .recordsFailed(1)
                    .recordsRejected(0)
                    .errors(List.of("CSV import failed: " + ex.getMessage()))
                    .message("CSV import failed: " + ex.getMessage())
                    .build());
        }
    }

    private UserPrincipal resolveCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up;
        }
        return null;
    }
}
