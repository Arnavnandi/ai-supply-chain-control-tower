package com.supplychain.controltower.controller;

import com.supplychain.controltower.service.CsvImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/data/import")
@RequiredArgsConstructor
@Slf4j
public class DataImportController {

    private final CsvImportService csvImportService;

    @PostMapping("/sample-dataset")
    public ResponseEntity<CsvImportService.ImportResult> importSampleDataset() {
        Long companyId = com.supplychain.controltower.security.TenantContext.getRequiredCompanyId();
        log.info("[REST API] Triggered 1-click sample dataset import for companyId: {}", companyId);
        CsvImportService.ImportResult result = csvImportService.importSampleDataset();
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/file")
    public ResponseEntity<?> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "entityType", defaultValue = "products") String entityType) {

        Long companyId = com.supplychain.controltower.security.TenantContext.getRequiredCompanyId();
        log.info("[REST API] File upload received for companyId {}: entityType '{}', filename '{}', size {} bytes",
                companyId, entityType, file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(CsvImportService.ImportResult.builder()
                    .success(false)
                    .entityType(entityType)
                    .totalRowsProcessed(0)
                    .recordsImported(0)
                    .recordsFailed(1)
                    .recordsRejected(0)
                    .errors(java.util.List.of("Uploaded CSV file is empty (0 bytes)."))
                    .message("Uploaded CSV file is empty.")
                    .build());
        }

        try {
            CsvImportService.ImportResult result = csvImportService.importSingleEntityFile(entityType, file.getInputStream());
            if (!result.isSuccess()) {
                return ResponseEntity.badRequest().body(result);
            }
            return ResponseEntity.ok(result);
        } catch (com.supplychain.controltower.exception.TenantContextMissingException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[REST API IMPORT FAIL] Error processing CSV upload: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(CsvImportService.ImportResult.builder()
                    .success(false)
                    .entityType(entityType)
                    .totalRowsProcessed(0)
                    .recordsImported(0)
                    .recordsFailed(1)
                    .recordsRejected(0)
                    .errors(java.util.List.of("CSV import failed: " + ex.getMessage()))
                    .message("CSV import failed: " + ex.getMessage())
                    .build());
        }
    }
}
