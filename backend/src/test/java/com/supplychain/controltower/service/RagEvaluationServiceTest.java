package com.supplychain.controltower.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagEvaluationServiceTest {

    @Mock
    private RagRetrievalService ragRetrievalService;

    @InjectMocks
    private RagEvaluationService ragEvaluationService;

    private RagRetrievalService.RetrievedChunk chunk1;
    private RagRetrievalService.RetrievedChunk chunk2;

    @BeforeEach
    void setUp() {
        chunk1 = RagRetrievalService.RetrievedChunk.builder()
                .chunkId("chunk-1")
                .sourceName("technical_documentation_report.md")
                .title("5. Dynamic Safety Stock & Inventory Optimization")
                .content("Safety Stock formula is SS = Z * sigma_d * sqrt(L)")
                .relevanceScore(0.03279)
                .build();

        chunk2 = RagRetrievalService.RetrievedChunk.builder()
                .chunkId("chunk-2")
                .sourceName("technical_documentation_report.md")
                .title("3. Database Schema")
                .content("Shipment database table schema details")
                .relevanceScore(0.01639)
                .build();
    }

    @Test
    void evaluateQueries_ShouldCalculateHitRateAndMrr_WhenMatchesFound() {
        when(ragRetrievalService.hybridSimilaritySearch(anyString(), anyInt()))
                .thenReturn(List.of(chunk1, chunk2));

        List<RagEvaluationService.BenchmarkTestCase> testCases = List.of(
                RagEvaluationService.BenchmarkTestCase.builder()
                        .query("What is the safety stock formula?")
                        .targetKeyword("Safety Stock")
                        .build()
        );

        RagEvaluationService.RagEvaluationReport report = ragEvaluationService.evaluateQueries(testCases, 4);

        assertNotNull(report);
        assertEquals(1, report.getTotalQueries());
        assertEquals(1.0, report.getHitRate());
        assertEquals(1.0, report.getMeanReciprocalRank()); // Rank 1 -> 1.0/1 = 1.0
        assertEquals(1, report.getQueryEvaluations().size());
        assertTrue(report.getQueryEvaluations().get(0).isHit());
        assertEquals(1, report.getQueryEvaluations().get(0).getRank());
    }

    @Test
    void evaluateQueries_ShouldHandleEmptyBenchmarkCases() {
        RagEvaluationService.RagEvaluationReport report = ragEvaluationService.evaluateQueries(Collections.emptyList(), 4);

        assertNotNull(report);
        assertEquals(0, report.getTotalQueries());
        assertEquals(0.0, report.getHitRate());
        assertEquals(0.0, report.getMeanReciprocalRank());
    }

    @Test
    void evaluateQueries_ShouldHandleNoHitCases() {
        when(ragRetrievalService.hybridSimilaritySearch(anyString(), anyInt()))
                .thenReturn(List.of(chunk2));

        List<RagEvaluationService.BenchmarkTestCase> testCases = List.of(
                RagEvaluationService.BenchmarkTestCase.builder()
                        .query("Unmatched query")
                        .targetKeyword("Nonexistent Keyword")
                        .build()
        );

        RagEvaluationService.RagEvaluationReport report = ragEvaluationService.evaluateQueries(testCases, 4);

        assertNotNull(report);
        assertEquals(1, report.getTotalQueries());
        assertEquals(0.0, report.getHitRate());
        assertEquals(0.0, report.getMeanReciprocalRank());
        assertFalse(report.getQueryEvaluations().get(0).isHit());
        assertEquals(0, report.getQueryEvaluations().get(0).getRank());
    }
}
