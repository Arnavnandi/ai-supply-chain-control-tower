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

    @InjectMocks
    private ActionApprovalService actionApprovalService;

    private Recommendation pendingRec;

    @BeforeEach
    void setUp() {
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
        when(recommendationRepository.findById(100L)).thenReturn(Optional.of(pendingRec));
        when(actionExecutionEngine.executeApprovedAction(anyString(), anyString(), anyString()))
                .thenReturn("Replenished 150 units of SKU-TEST-001");
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));

        Recommendation result = actionApprovalService.approveAndExecute(100L, "ManagerJohn");

        assertNotNull(result);
        assertEquals(Recommendation.ApprovalStatus.EXECUTED, result.getStatus());
        assertEquals("ManagerJohn", result.getExecutedBy());
        verify(actionExecutionEngine, times(1)).executeApprovedAction(anyString(), anyString(), anyString());
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void testRejectRecommendation() {
        when(recommendationRepository.findById(100L)).thenReturn(Optional.of(pendingRec));
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));

        Recommendation result = actionApprovalService.rejectRecommendation(100L, "ManagerJohn");

        assertNotNull(result);
        assertEquals(Recommendation.ApprovalStatus.REJECTED, result.getStatus());
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }
}
