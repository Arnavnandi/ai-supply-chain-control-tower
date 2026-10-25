package com.supplychain.controltower.service;

import com.supplychain.controltower.entity.AuditLog;
import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.repository.AuditLogRepository;
import com.supplychain.controltower.repository.RecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionApprovalServiceTest {

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ActionExecutionEngine actionExecutionEngine;

    @Mock
    private com.supplychain.controltower.repository.CompanyRepository companyRepository;

    @InjectMocks
    private ActionApprovalService actionApprovalService;

    private Recommendation pendingRec;

    @BeforeEach
    void setUp() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        lenient().when(companyRepository.findById(anyLong())).thenReturn(Optional.of(com.supplychain.controltower.entity.Company.builder().id(1L).code("DEMO").name("Demo Corp").build()));
        pendingRec = Recommendation.builder()
                .id(100L)
                .title("Reorder Valve Stock")
                .type(Recommendation.RecommendationType.REORDER_STOCK)
                .actionPayloadJson("{\"productId\":1,\"orderQuantity\":150}")
                .reasoning("Stock level below safety threshold")
                .status(Recommendation.ApprovalStatus.PENDING_APPROVAL)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testApproveAndExecuteRecommendation() {
        when(recommendationRepository.findByCompanyIdAndId(1L, 100L)).thenReturn(Optional.of(pendingRec));
        when(actionExecutionEngine.executeApprovedAction(anyString(), anyString(), anyString()))
                .thenReturn("Replenished 150 units of SKU-TEST-001");
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));

        Recommendation result = actionApprovalService.approveAndExecute(100L, "ManagerJohn");

        assertNotNull(result);
        assertEquals(Recommendation.ApprovalStatus.EXECUTED, result.getStatus());
        assertEquals("ManagerJohn", result.getExecutedBy());
        verify(actionExecutionEngine, times(1)).executeApprovedAction(anyString(), anyString(), anyString());

        org.mockito.ArgumentCaptor<AuditLog> logCaptor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(logCaptor.capture());
        assertEquals(1L, logCaptor.getValue().getUserId(), "Default userId should be 1L when not explicitly provided");
        assertEquals("ManagerJohn", logCaptor.getValue().getUsername());
    }

    @Test
    void testApproveAndExecuteRecommendation_WithExplicitUserId_RecordsInAuditLog() {
        when(recommendationRepository.findByCompanyIdAndId(1L, 100L)).thenReturn(Optional.of(pendingRec));
        when(actionExecutionEngine.executeApprovedAction(anyString(), anyString(), anyString()))
                .thenReturn("Replenished 150 units of SKU-TEST-001");
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));

        Recommendation result = actionApprovalService.approveAndExecute(100L, "AdminSarah", 42L);

        assertNotNull(result);
        assertEquals(Recommendation.ApprovalStatus.EXECUTED, result.getStatus());
        assertEquals("AdminSarah", result.getExecutedBy());

        org.mockito.ArgumentCaptor<AuditLog> logCaptor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(logCaptor.capture());
        assertEquals(42L, logCaptor.getValue().getUserId(), "AuditLog must record authenticated user ID 42L");
        assertEquals("AdminSarah", logCaptor.getValue().getUsername());
    }

    @Test
    void testRejectRecommendation_WithExplicitUserId_RecordsInAuditLog() {
        when(recommendationRepository.findByCompanyIdAndId(1L, 100L)).thenReturn(Optional.of(pendingRec));
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));

        Recommendation result = actionApprovalService.rejectRecommendation(100L, "AdminSarah", 88L);

        assertNotNull(result);
        assertEquals(Recommendation.ApprovalStatus.REJECTED, result.getStatus());

        org.mockito.ArgumentCaptor<AuditLog> logCaptor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(logCaptor.capture());
        assertEquals(88L, logCaptor.getValue().getUserId(), "AuditLog must record authenticated user ID 88L");
        assertEquals("AdminSarah", logCaptor.getValue().getUsername());
    }

    @Test
    void testRejectRecommendation() {
        when(recommendationRepository.findByCompanyIdAndId(1L, 100L)).thenReturn(Optional.of(pendingRec));
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));

        Recommendation result = actionApprovalService.rejectRecommendation(100L, "ManagerJohn");

        assertNotNull(result);
        assertEquals(Recommendation.ApprovalStatus.REJECTED, result.getStatus());
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void testDuplicateExecutionRejection() {
        Recommendation executedRec = Recommendation.builder()
                .id(200L)
                .status(Recommendation.ApprovalStatus.EXECUTED)
                .build();

        when(recommendationRepository.findByCompanyIdAndId(1L, 200L)).thenReturn(Optional.of(executedRec));

        assertThrows(RuntimeException.class, () ->
                actionApprovalService.approveAndExecute(200L, "ManagerJohn")
        );
    }
}
