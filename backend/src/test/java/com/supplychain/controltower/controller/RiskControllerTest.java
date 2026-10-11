package com.supplychain.controltower.controller;

import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.RiskAlert;
import com.supplychain.controltower.exception.ResourceNotFoundException;
import com.supplychain.controltower.exception.TenantContextMissingException;
import com.supplychain.controltower.repository.RiskAlertRepository;
import com.supplychain.controltower.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RiskControllerTest {

    private RiskAlertRepository riskAlertRepository;
    private RiskController riskController;

    private static final Long COMPANY_A_ID = 101L;
    private static final Long COMPANY_B_ID = 202L;

    private Company companyA;
    private Company companyB;
    private RiskAlert alertAActive;
    private RiskAlert alertAResolved;
    private RiskAlert alertBActive;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        riskAlertRepository = mock(RiskAlertRepository.class);
        riskController = new RiskController(riskAlertRepository);

        companyA = Company.builder().id(COMPANY_A_ID).name("Company Alpha").code("ALPHA").build();
        companyB = Company.builder().id(COMPANY_B_ID).name("Company Beta").code("BETA").build();

        alertAActive = RiskAlert.builder()
                .id(1L)
                .company(companyA)
                .riskCategory(RiskAlert.RiskCategory.STOCKOUT)
                .severityLevel(RiskAlert.SeverityLevel.CRITICAL)
                .entityType("Product")
                .entityId(10L)
                .description("Company A Stockout")
                .recommendationText("Reorder stock immediately")
                .status(RiskAlert.RiskStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();

        alertAResolved = RiskAlert.builder()
                .id(2L)
                .company(companyA)
                .riskCategory(RiskAlert.RiskCategory.SHIPMENT_DELAY)
                .severityLevel(RiskAlert.SeverityLevel.MEDIUM)
                .entityType("Shipment")
                .entityId(20L)
                .description("Company A Resolved Delay")
                .recommendationText("Track carrier")
                .status(RiskAlert.RiskStatus.RESOLVED)
                .createdAt(LocalDateTime.now().minusHours(5))
                .build();

        alertBActive = RiskAlert.builder()
                .id(99L)
                .company(companyB)
                .riskCategory(RiskAlert.RiskCategory.WAREHOUSE_CAPACITY)
                .severityLevel(RiskAlert.SeverityLevel.HIGH)
                .entityType("Warehouse")
                .entityId(30L)
                .description("Company B Warehouse Overflow")
                .recommendationText("Transfer inventory")
                .status(RiskAlert.RiskStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Company A listing all risks returns only Company A's alerts, scoped by tenant")
    void testGetAllRisksScopesToAuthenticatedTenant() {
        TenantContext.setCompanyId(COMPANY_A_ID);
        when(riskAlertRepository.findByCompanyIdOrderByCreatedAtDesc(COMPANY_A_ID))
                .thenReturn(List.of(alertAActive, alertAResolved));

        ResponseEntity<List<RiskAlert>> response = riskController.getAllRisks(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertTrue(response.getBody().stream().allMatch(a -> a.getCompany().getId().equals(COMPANY_A_ID)));
        verify(riskAlertRepository, times(1)).findByCompanyIdOrderByCreatedAtDesc(COMPANY_A_ID);
        verify(riskAlertRepository, never()).findByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("Company A filtering by status returns only Company A's alerts matching that status")
    void testGetAllRisksWithStatusFilterScopesToAuthenticatedTenant() {
        TenantContext.setCompanyId(COMPANY_A_ID);
        when(riskAlertRepository.findByCompanyIdAndStatus(COMPANY_A_ID, RiskAlert.RiskStatus.ACTIVE))
                .thenReturn(List.of(alertAActive));

        ResponseEntity<List<RiskAlert>> response = riskController.getAllRisks(RiskAlert.RiskStatus.ACTIVE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Company A Stockout", response.getBody().get(0).getDescription());
        assertEquals(RiskAlert.RiskStatus.ACTIVE, response.getBody().get(0).getStatus());
        verify(riskAlertRepository, times(1)).findByCompanyIdAndStatus(COMPANY_A_ID, RiskAlert.RiskStatus.ACTIVE);
        verify(riskAlertRepository, never()).findByStatus(any());
    }

    @Test
    @DisplayName("Company A fetching active risks returns only Company A's active alerts")
    void testGetActiveRisksScopesToAuthenticatedTenant() {
        TenantContext.setCompanyId(COMPANY_A_ID);
        when(riskAlertRepository.findByCompanyIdAndStatus(COMPANY_A_ID, RiskAlert.RiskStatus.ACTIVE))
                .thenReturn(List.of(alertAActive));

        ResponseEntity<List<RiskAlert>> response = riskController.getActiveRisks();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(1L, response.getBody().get(0).getId());
        verify(riskAlertRepository, times(1)).findByCompanyIdAndStatus(COMPANY_A_ID, RiskAlert.RiskStatus.ACTIVE);
    }

    @Test
    @DisplayName("Company A cannot count Company B's risk alerts (unfiltered and status-filtered)")
    void testGetRiskCountScopesToAuthenticatedTenant() {
        TenantContext.setCompanyId(COMPANY_A_ID);
        when(riskAlertRepository.countByCompanyId(COMPANY_A_ID)).thenReturn(2L);
        when(riskAlertRepository.countByCompanyIdAndStatus(COMPANY_A_ID, RiskAlert.RiskStatus.ACTIVE)).thenReturn(1L);

        // Unfiltered count
        ResponseEntity<Map<String, Long>> countAllResponse = riskController.getRiskCount(null);
        assertEquals(HttpStatus.OK, countAllResponse.getStatusCode());
        assertNotNull(countAllResponse.getBody());
        assertEquals(2L, countAllResponse.getBody().get("count"));
        verify(riskAlertRepository, times(1)).countByCompanyId(COMPANY_A_ID);

        // Status-filtered count
        ResponseEntity<Map<String, Long>> countActiveResponse = riskController.getRiskCount(RiskAlert.RiskStatus.ACTIVE);
        assertEquals(HttpStatus.OK, countActiveResponse.getStatusCode());
        assertNotNull(countActiveResponse.getBody());
        assertEquals(1L, countActiveResponse.getBody().get("count"));
        verify(riskAlertRepository, times(1)).countByCompanyIdAndStatus(COMPANY_A_ID, RiskAlert.RiskStatus.ACTIVE);
    }

    @Test
    @DisplayName("Company A cannot access Company B's risk alert by guessing its ID (throws 404)")
    void testGetRiskByIdDeniesCrossTenantAccess() {
        TenantContext.setCompanyId(COMPANY_A_ID);
        Long alertBId = alertBActive.getId(); // 99L

        // Under Company A's context, looking up 99L returns empty because it belongs to Company B
        when(riskAlertRepository.findByCompanyIdAndId(COMPANY_A_ID, alertBId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                riskController.getRiskById(alertBId));

        assertEquals("Risk alert not found with id: 99", ex.getMessage());
        verify(riskAlertRepository, times(1)).findByCompanyIdAndId(COMPANY_A_ID, alertBId);
        verify(riskAlertRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Company B can successfully access its own alert by ID")
    void testGetRiskByIdAllowsAuthorizedTenantAccess() {
        TenantContext.setCompanyId(COMPANY_B_ID);
        Long alertBId = alertBActive.getId();

        when(riskAlertRepository.findByCompanyIdAndId(COMPANY_B_ID, alertBId)).thenReturn(Optional.of(alertBActive));

        ResponseEntity<RiskAlert> response = riskController.getRiskById(alertBId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(alertBId, response.getBody().getId());
        assertEquals("Company B Warehouse Overflow", response.getBody().getDescription());
        assertEquals(COMPANY_B_ID, response.getBody().getCompany().getId());
        verify(riskAlertRepository, times(1)).findByCompanyIdAndId(COMPANY_B_ID, alertBId);
    }

    @Test
    @DisplayName("Fail-closed security: Missing tenant context throws TenantContextMissingException")
    void testFailClosedWhenTenantContextMissing() {
        TenantContext.clear(); // No authenticated company in context

        assertThrows(TenantContextMissingException.class, () -> riskController.getAllRisks(null));
        assertThrows(TenantContextMissingException.class, () -> riskController.getActiveRisks());
        assertThrows(TenantContextMissingException.class, () -> riskController.getRiskById(1L));
        assertThrows(TenantContextMissingException.class, () -> riskController.getRiskCount(null));

        verifyNoInteractions(riskAlertRepository);
    }
}
