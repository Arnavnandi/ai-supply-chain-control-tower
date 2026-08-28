package com.supplychain.controltower.ai.tools;

import com.supplychain.controltower.analytics.RiskAnalysisEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RiskTools {

    private final RiskAnalysisEngine riskAnalysisEngine;

    @Description("Retrieves active supply chain risk alerts across inventory, suppliers, and logistics with explainability metadata (problem detected, raw database metric causes, and recommended mitigation actions).")
    public RiskReportRecord getActiveSupplyChainRisks() {
        log.info("[SPRING AI TOOL EXECUTING] getActiveSupplyChainRisks() evaluating database risks...");
        RiskAnalysisEngine.ControlTowerRiskReport report = riskAnalysisEngine.evaluateSystemRisks();

        List<RiskItemRecord> items = report.getRiskItems().stream().map(r ->
                new RiskItemRecord(
                        r.getId(),
                        r.getCategory(),
                        r.getTitle(),
                        r.getSeverity(),
                        r.getProblemDetected(),
                        r.getDataCause(),
                        r.getActionRecommended()
                )
        ).toList();

        log.info("[SPRING AI TOOL COMPLETE] getActiveSupplyChainRisks() evaluated risk score: {} (level: {}).",
                report.getOverallRiskScore(), report.getRiskLevel());

        return new RiskReportRecord(
                report.getOverallRiskScore(),
                report.getRiskLevel(),
                report.getCriticalRisksCount(),
                report.getHighRisksCount(),
                report.getMediumRisksCount(),
                items
        );
    }

    public record RiskReportRecord(
            double overallRiskScore,
            String riskLevel,
            int criticalCount,
            int highCount,
            int mediumCount,
            List<RiskItemRecord> riskItems
    ) {}

    public record RiskItemRecord(
            String id,
            String category,
            String title,
            String severity,
            String problemDetected,
            String dataCause,
            String actionRecommended
    ) {}
}
