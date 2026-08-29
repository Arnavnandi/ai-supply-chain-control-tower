package com.supplychain.controltower.analytics;

import com.supplychain.controltower.repository.AuditLogRepository;
import com.supplychain.controltower.repository.RecommendationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(recommendationRepository.count()).thenReturn(15L);

        HistoricalMitigationEfficacyEngine.HistoricalEfficacyReport report =
                engine.calculateHistoricalEfficacy();

        assertNotNull(report);
        assertEquals(15, report.getTotalHistoricalExecutions());
        assertTrue(report.getOverallSuccessRatePct() > 80.0);
        assertTrue(report.getOverallAverageRiskReductionDelta() < 0);
        assertEquals(4, report.getCategoryBreakdowns().size());
    }
}
