package com.supplychain.controltower.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagEvaluationService {

    private final RagRetrievalService ragRetrievalService;

    @Data
    @Builder
    public static class BenchmarkTestCase {
        private String query;
        private String targetKeyword;
    }

    @Data
    @Builder
    public static class EvaluationQueryResult {
        private String query;
        private String targetKeyword;
        private boolean hit;
        private int rank; // 1-based rank, or 0 if not hit
        private double reciprocalRank;
        private List<String> topRetrievedTitles;
    }

    @Data
    @Builder
    public static class RagEvaluationReport {
        private int totalQueries;
        private int topK;
        private double hitRate;
        private double meanReciprocalRank;
        private List<EvaluationQueryResult> queryEvaluations;
    }

    public static final List<BenchmarkTestCase> GOLDEN_BENCHMARK_SET = List.of(
            BenchmarkTestCase.builder().query("What is the dynamic safety stock formula?").targetKeyword("Dynamic Safety Stock").build(),
            BenchmarkTestCase.builder().query("Explain out of sample backtesting MAPE and RMSE").targetKeyword("Demand Forecasting").build(),
            BenchmarkTestCase.builder().query("How is the weighted moving average calculated?").targetKeyword("Demand Forecasting").build(),
            BenchmarkTestCase.builder().query("How is supplier OTIF metric evaluated?").targetKeyword("Supplier Analytics").build(),
            BenchmarkTestCase.builder().query("What is the multi-factor risk score calculation?").targetKeyword("Risk").build(),
            BenchmarkTestCase.builder().query("How does purchase order replenishment work?").targetKeyword("Purchase Order").build(),
            BenchmarkTestCase.builder().query("What is what-if disruption simulation?").targetKeyword("Stress Testing").build(),
            BenchmarkTestCase.builder().query("What PostgreSQL database tables store shipments?").targetKeyword("Database Schema").build()
    );

    /**
     * Evaluates RAG retrieval precision across standard golden benchmark queries using Hit Rate @ K and MRR.
     */
    public RagEvaluationReport evaluateRagRetrieval(int topK) {
        return evaluateQueries(GOLDEN_BENCHMARK_SET, topK);
    }

    public RagEvaluationReport evaluateQueries(List<BenchmarkTestCase> benchmarkCases, int topK) {
        if (benchmarkCases == null || benchmarkCases.isEmpty()) {
            return RagEvaluationReport.builder()
                    .totalQueries(0)
                    .topK(topK)
                    .hitRate(0.0)
                    .meanReciprocalRank(0.0)
                    .queryEvaluations(List.of())
                    .build();
        }

        List<EvaluationQueryResult> evaluations = new ArrayList<>();
        double totalReciprocalRank = 0.0;
        int hitCount = 0;

        for (BenchmarkTestCase testCase : benchmarkCases) {
            List<RagRetrievalService.RetrievedChunk> retrieved = ragRetrievalService.hybridSimilaritySearch(testCase.getQuery(), topK);

            List<String> titles = retrieved.stream().map(RagRetrievalService.RetrievedChunk::getTitle).collect(Collectors.toList());

            int hitRank = 0;
            String lowerTarget = testCase.getTargetKeyword().toLowerCase();

            for (int i = 0; i < retrieved.size(); i++) {
                RagRetrievalService.RetrievedChunk chunk = retrieved.get(i);
                String titleStr = chunk.getTitle() != null ? chunk.getTitle().toLowerCase() : "";
                String contentStr = chunk.getContent() != null ? chunk.getContent().toLowerCase() : "";

                if (titleStr.contains(lowerTarget) || contentStr.contains(lowerTarget)) {
                    hitRank = i + 1; // 1-based rank
                    break;
                }
            }

            boolean isHit = hitRank > 0;
            double rr = isHit ? (1.0 / hitRank) : 0.0;

            if (isHit) {
                hitCount++;
            }
            totalReciprocalRank += rr;

            evaluations.add(EvaluationQueryResult.builder()
                    .query(testCase.getQuery())
                    .targetKeyword(testCase.getTargetKeyword())
                    .hit(isHit)
                    .rank(hitRank)
                    .reciprocalRank(Math.round(rr * 10000.0) / 10000.0)
                    .topRetrievedTitles(titles)
                    .build());
        }

        int queryCount = benchmarkCases.size();
        double rawHitRate = (double) hitCount / queryCount;
        double rawMrr = totalReciprocalRank / queryCount;

        double hitRate = Math.round(rawHitRate * 10000.0) / 10000.0;
        double mrr = Math.round(rawMrr * 10000.0) / 10000.0;

        log.info("[RAG EVALUATION COMPLETE] Queries evaluated: {} | TopK: {} | Hit Rate: {} | MRR: {}",
                queryCount, topK, hitRate, mrr);

        return RagEvaluationReport.builder()
                .totalQueries(queryCount)
                .topK(topK)
                .hitRate(hitRate)
                .meanReciprocalRank(mrr)
                .queryEvaluations(evaluations)
                .build();
    }
}
