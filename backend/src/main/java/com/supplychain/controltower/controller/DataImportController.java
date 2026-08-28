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
        log.info("[REST API] Triggered 1-click sample dataset import...");
        CsvImportService.ImportResult result = csvImportService.importSampleDataset();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/file")
    public ResponseEntity<?> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "entityType", defaultValue = "products") String entityType) {

        log.info("[REST API] File upload received for entityType '{}': filename='{}', size={} bytes",
                entityType, file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Uploaded CSV file is empty"));
        }

        try {
            CsvImportService.ImportResult result = csvImportService.importSingleEntityFile(entityType, file.getInputStream());
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            log.error("[REST API IMPORT FAIL] Error processing CSV upload: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "entityType", entityType,
                    "message", "CSV import failed: " + ex.getMessage()
            ));
        }
    }
}
