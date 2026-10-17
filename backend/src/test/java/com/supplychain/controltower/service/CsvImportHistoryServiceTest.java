package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.CsvImportHistoryDto;
import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.CsvImportHistory;
import com.supplychain.controltower.repository.CompanyRepository;
import com.supplychain.controltower.repository.CsvImportHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CsvImportHistoryServiceTest {

    private CsvImportHistoryRepository historyRepository;
    private CompanyRepository companyRepository;
    private CsvImportHistoryService historyService;

    private Company demoCompany;

    @BeforeEach
    void setUp() {
        historyRepository = mock(CsvImportHistoryRepository.class);
        companyRepository = mock(CompanyRepository.class);
        historyService = new CsvImportHistoryService(historyRepository, companyRepository);

        demoCompany = Company.builder().id(1L).code("DEMO").name("Demo Corp").status("ACTIVE").build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(demoCompany));
    }

    @Test
    @DisplayName("1. recordImportStart creates history record with IN_PROGRESS status")
    void testRecordImportStart_CreatesHistoryInProgress() {
        when(historyRepository.save(any(CsvImportHistory.class))).thenAnswer(invocation -> {
            CsvImportHistory h = invocation.getArgument(0);
            h.setId(101L);
            return h;
        });

        Long historyId = historyService.recordImportStart(
                1L, 5L, "john_manager", "products.csv", "products", 1024L, "abc123hash");

        assertNotNull(historyId);
        assertEquals(101L, historyId);

        ArgumentCaptor<CsvImportHistory> captor = ArgumentCaptor.forClass(CsvImportHistory.class);
        verify(historyRepository, times(1)).save(captor.capture());

        CsvImportHistory saved = captor.getValue();
        assertEquals(demoCompany, saved.getCompany());
        assertEquals(5L, saved.getUserId());
        assertEquals("john_manager", saved.getUsername());
        assertEquals("products.csv", saved.getFilename());
        assertEquals("products", saved.getEntityType());
        assertEquals(CsvImportHistory.ImportStatus.IN_PROGRESS, saved.getStatus());
        assertEquals("abc123hash", saved.getFileHash());
        assertEquals(1024L, saved.getFileSizeBytes());
    }

    @Test
    @DisplayName("2. recordImportSuccess updates status to IMPORTED with row counts")
    void testRecordImportSuccess_UpdatesStatusToImported() {
        CsvImportHistory existing = CsvImportHistory.builder()
                .id(101L)
                .company(demoCompany)
                .status(CsvImportHistory.ImportStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusSeconds(5))
                .build();

        when(historyRepository.findById(101L)).thenReturn(Optional.of(existing));

        CsvImportService.ImportResult result = CsvImportService.ImportResult.builder()
                .success(true)
                .entityType("products")
                .totalRowsProcessed(50)
                .recordsImported(50)
                .recordsFailed(0)
                .build();

        historyService.recordImportSuccess(101L, result);

        verify(historyRepository, times(1)).save(existing);
        assertEquals(CsvImportHistory.ImportStatus.IMPORTED, existing.getStatus());
        assertEquals(50, existing.getTotalRows());
        assertEquals(50, existing.getRecordsImported());
        assertEquals(0, existing.getRecordsFailed());
        assertNotNull(existing.getCompletedAt());
    }

    @Test
    @DisplayName("3. recordImportFailure updates status to FAILED and records error summary")
    void testRecordImportFailure_UpdatesStatusToFailed() {
        CsvImportHistory existing = CsvImportHistory.builder()
                .id(102L)
                .company(demoCompany)
                .status(CsvImportHistory.ImportStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusSeconds(2))
                .build();

        when(historyRepository.findById(102L)).thenReturn(Optional.of(existing));

        List<String> errors = List.of(
                "Row 2: Missing required column 'sku'",
                "Row 4: Invalid price format 'abc'"
        );

        historyService.recordImportFailure(102L, 5, 2, errors, "Validation failed on 2 rows");

        verify(historyRepository, times(1)).save(existing);
        assertEquals(CsvImportHistory.ImportStatus.FAILED, existing.getStatus());
        assertEquals(5, existing.getTotalRows());
        assertEquals(2, existing.getRecordsFailed());
        assertNotNull(existing.getErrorSummary());
        assertTrue(existing.getErrorSummary().contains("Missing required column 'sku'"));
        assertTrue(existing.getErrorSummary().contains("Validation failed on 2 rows"));
        assertNotNull(existing.getCompletedAt());
    }

    @Test
    @DisplayName("4. getTenantImportHistory enforces tenant isolation and detects duplicates")
    void testGetTenantImportHistory_TenantIsolationAndDuplicateDetection() {
        CsvImportHistory h1 = CsvImportHistory.builder()
                .id(1L)
                .company(demoCompany)
                .filename("orders_batch1.csv")
                .entityType("orders")
                .status(CsvImportHistory.ImportStatus.IMPORTED)
                .fileHash("hash_shared")
                .totalRows(10)
                .recordsImported(10)
                .startedAt(LocalDateTime.now().minusHours(2))
                .build();

        CsvImportHistory h2 = CsvImportHistory.builder()
                .id(2L)
                .company(demoCompany)
                .filename("orders_batch2_copy.csv")
                .entityType("orders")
                .status(CsvImportHistory.ImportStatus.IMPORTED)
                .fileHash("hash_shared")
                .totalRows(10)
                .recordsImported(10)
                .startedAt(LocalDateTime.now().minusHours(1))
                .build();

        when(historyRepository.findByCompanyIdOrderByStartedAtDesc(1L)).thenReturn(List.of(h2, h1));

        List<CsvImportHistoryDto> historyDtos = historyService.getTenantImportHistory(1L);

        assertEquals(2, historyDtos.size());
        assertEquals(1L, historyDtos.get(0).getCompanyId());
        // h2 was uploaded after h1 with the same hash, so h2 is flagged as duplicate while original h1 is not
        assertTrue(historyDtos.get(0).isDuplicateFile());
        assertFalse(historyDtos.get(1).isDuplicateFile());

        verify(historyRepository, times(1)).findByCompanyIdOrderByStartedAtDesc(1L);
        verify(historyRepository, never()).findByCompanyIdOrderByStartedAtDesc(eq(2L));
    }

    @Test
    @DisplayName("5. isDuplicateFile returns true only when matching hash previously IMPORTED for tenant")
    void testIsDuplicateFile() {
        when(historyRepository.existsByCompanyIdAndFileHashAndStatus(
                1L, "existing_hash", CsvImportHistory.ImportStatus.IMPORTED)).thenReturn(true);
        when(historyRepository.existsByCompanyIdAndFileHashAndStatus(
                1L, "new_hash", CsvImportHistory.ImportStatus.IMPORTED)).thenReturn(false);

        assertTrue(historyService.isDuplicateFile(1L, "existing_hash"));
        assertFalse(historyService.isDuplicateFile(1L, "new_hash"));
        assertFalse(historyService.isDuplicateFile(1L, ""));
        assertFalse(historyService.isDuplicateFile(1L, null));
    }

    @Test
    @DisplayName("6. computeSha256 produces deterministic lowercase 64-character hash")
    void testComputeSha256() {
        byte[] content = "sku,name,price\nSKU-1,Widget,10.00\n".getBytes();
        String hash1 = historyService.computeSha256(content);
        String hash2 = historyService.computeSha256(content);

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
        assertTrue(hash1.matches("^[a-f0-9]{64}$"));
    }
}
