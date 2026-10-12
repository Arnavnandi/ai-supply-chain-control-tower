package com.supplychain.controltower.analytics;

import com.supplychain.controltower.repository.AuditLogRepository;
import com.supplychain.controltower.repository.RecommendationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalMitigationEfficacyEngineTest {

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private HistoricalMitigationEfficacyEngine engine;

    @Test
    void testCalculateHistoricalEfficacy() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        List<com.supplychain.controltower.entity.Recommendation> recs = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> com.supplychain.controltower.entity.Recommendation.builder()
                        .type(com.supplychain.controltower.entity.Recommendation.RecommendationType.REORDER_STOCK)
                        .status(com.supplychain.controltower.entity.Recommendation.ApprovalStatus.EXECUTED)
                        .build())
                .toList();
        when(recommendationRepository.findByCompanyId(1L)).thenReturn(recs);

        HistoricalMitigationEfficacyEngine.HistoricalEfficacyReport report =
                engine.calculateHistoricalEfficacy();

        assertNotNull(report);
        assertEquals(15, report.getTotalHistoricalExecutions());
        assertTrue(report.getOverallSuccessRatePct() > 80.0);
        assertTrue(report.getOverallAverageRiskReductionDelta() < 0);
        assertFalse(report.getCategoryBreakdowns().isEmpty());
    }

    @Test
    void testCalculateHistoricalEfficacyWithEmptyData() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        when(recommendationRepository.findByCompanyId(1L)).thenReturn(List.of());

        HistoricalMitigationEfficacyEngine.HistoricalEfficacyReport report =
                engine.calculateHistoricalEfficacy();

        assertNotNull(report);
        assertEquals(0, report.getTotalHistoricalExecutions());
        assertEquals(0.0, report.getOverallSuccessRatePct());
        assertTrue(report.getCategoryBreakdowns().isEmpty());
        assertTrue(report.getHistoricalInsightsSummary().contains("Insufficient historical mitigation data"));
    }
}
