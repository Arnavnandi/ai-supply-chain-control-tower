package com.supplychain.controltower.controller;

import com.supplychain.controltower.dto.domain.CsvImportHistoryDto;
import com.supplychain.controltower.security.TenantContext;
import com.supplychain.controltower.security.UserPrincipal;
import com.supplychain.controltower.service.CsvImportHistoryService;
import com.supplychain.controltower.service.CsvImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataImportControllerTest {

    private CsvImportService csvImportService;
    private CsvImportHistoryService csvImportHistoryService;
    private DataImportController controller;

    private UserPrincipal testPrincipal;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(1L);
        csvImportService = mock(CsvImportService.class);
        csvImportHistoryService = mock(CsvImportHistoryService.class);
        controller = new DataImportController(csvImportService, csvImportHistoryService);

        testPrincipal = new UserPrincipal(
                10L, "test_admin", "admin@supplychain.com", "pass",
                1L, "DEMO", "ACTIVE", List.of()
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("1. GET /api/data/import/history returns tenant-scoped history")
    void testGetImportHistory_ReturnsTenantScopedHistory() {
        CsvImportHistoryDto dto1 = CsvImportHistoryDto.builder()
                .id(1L)
                .companyId(1L)
                .filename("products.csv")
                .status("IMPORTED")
                .totalRows(25)
                .recordsImported(25)
                .build();

        when(csvImportHistoryService.getTenantImportHistory(1L)).thenReturn(List.of(dto1));

        ResponseEntity<List<CsvImportHistoryDto>> response = controller.getImportHistory();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("products.csv", response.getBody().get(0).getFilename());
        verify(csvImportHistoryService, times(1)).getTenantImportHistory(1L);
    }

    @Test
    @DisplayName("2. POST /api/data/import/file successful upload records history as IMPORTED")
    void testImportFile_Success_RecordsHistory() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "products.csv", "text/csv",
                "sku,name,price\nSKU-1,Prod 1,50.00\n".getBytes(StandardCharsets.UTF_8));

        when(csvImportHistoryService.computeSha256(any())).thenReturn("sha256_mock_hash");
        when(csvImportHistoryService.isDuplicateFile(eq(1L), anyString())).thenReturn(false);
        when(csvImportHistoryService.recordImportStart(eq(1L), eq(10L), eq("test_admin"), eq("products.csv"), eq("products"), anyLong(), eq("sha256_mock_hash")))
                .thenReturn(55L);

        CsvImportService.ImportResult successResult = CsvImportService.ImportResult.builder()
                .success(true)
                .entityType("products")
                .totalRowsProcessed(1)
                .recordsImported(1)
                .recordsFailed(0)
                .message("Products imported successfully")
                .build();

        when(csvImportService.importSingleEntityFile(eq("products"), any())).thenReturn(successResult);

        ResponseEntity<?> response = controller.importFile(file, "products", testPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof CsvImportService.ImportResult);
        CsvImportService.ImportResult body = (CsvImportService.ImportResult) response.getBody();
        assertTrue(body.isSuccess());

        verify(csvImportHistoryService, times(1)).recordImportStart(
                eq(1L), eq(10L), eq("test_admin"), eq("products.csv"), eq("products"), anyLong(), eq("sha256_mock_hash"));
        verify(csvImportHistoryService, times(1)).recordImportSuccess(eq(55L), eq(successResult));
        verify(csvImportHistoryService, never()).recordImportFailure(anyLong(), anyInt(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("3. POST /api/data/import/file failed validation records history as FAILED")
    void testImportFile_ValidationFailure_RecordsHistory() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid_products.csv", "text/csv",
                "sku,name,price\n,Missing SKU,50.00\n".getBytes(StandardCharsets.UTF_8));

        when(csvImportHistoryService.computeSha256(any())).thenReturn("sha256_fail_hash");
        when(csvImportHistoryService.recordImportStart(eq(1L), eq(10L), eq("test_admin"), eq("invalid_products.csv"), eq("products"), anyLong(), eq("sha256_fail_hash")))
                .thenReturn(56L);

        CsvImportService.ImportResult failResult = CsvImportService.ImportResult.builder()
                .success(false)
                .entityType("products")
                .totalRowsProcessed(1)
                .recordsImported(0)
                .recordsFailed(1)
                .errors(List.of("Row 1: Missing required column 'sku'"))
                .message("Import failed due to validation errors")
                .build();

        when(csvImportService.importSingleEntityFile(eq("products"), any())).thenReturn(failResult);

        ResponseEntity<?> response = controller.importFile(file, "products", testPrincipal);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof CsvImportService.ImportResult);
        CsvImportService.ImportResult body = (CsvImportService.ImportResult) response.getBody();
        assertFalse(body.isSuccess());

        verify(csvImportHistoryService, times(1)).recordImportFailure(
                eq(56L), eq(1), eq(1), eq(failResult.getErrors()), eq(failResult.getMessage()));
        verify(csvImportHistoryService, never()).recordImportSuccess(anyLong(), any());
    }

    @Test
    @DisplayName("4. POST /api/data/import/file duplicate file warns user without blocking import")
    void testImportFile_DuplicateFile_WarnsWithoutBlocking() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "products_again.csv", "text/csv",
                "sku,name,price\nSKU-1,Prod 1,50.00\n".getBytes(StandardCharsets.UTF_8));

        when(csvImportHistoryService.computeSha256(any())).thenReturn("sha256_existing_hash");
        // Duplicate detected!
        when(csvImportHistoryService.isDuplicateFile(eq(1L), eq("sha256_existing_hash"))).thenReturn(true);
        when(csvImportHistoryService.recordImportStart(anyLong(), any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(57L);

        CsvImportService.ImportResult successResult = CsvImportService.ImportResult.builder()
                .success(true)
                .entityType("products")
                .totalRowsProcessed(1)
                .recordsImported(1)
                .recordsFailed(0)
                .build();

        when(csvImportService.importSingleEntityFile(eq("products"), any())).thenReturn(successResult);

        ResponseEntity<?> response = controller.importFile(file, "products", testPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof CsvImportService.ImportResult);
        CsvImportService.ImportResult body = (CsvImportService.ImportResult) response.getBody();

        // Import was NOT blocked!
        assertTrue(body.isSuccess());
        // Duplicate was detected and warning was attached
        assertTrue(body.isDuplicateDetected());
        assertNotNull(body.getDuplicateWarning());
        assertTrue(body.getDuplicateWarning().contains("Duplicate file warning"));
    }
}
