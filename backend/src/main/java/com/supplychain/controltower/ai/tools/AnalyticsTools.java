package com.supplychain.controltower.ai.tools;

import com.supplychain.controltower.repository.RiskAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsTools {

    private final RiskAlertRepository riskAlertRepository;

    @Description("Retrieves active supply chain risk alerts across inventory, suppliers, warehouses, and logistics.")
    public List<RiskAlertRecord> getSupplyChainRisks() {
        Long companyId = com.supplychain.controltower.security.TenantContext.getRequiredCompanyId();
        log.info("[SPRING AI TOOL EXECUTING] getSupplyChainRisks() querying PostgreSQL database for companyId: {}...", companyId);
        List<com.supplychain.controltower.entity.RiskAlert> alerts = riskAlertRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        List<RiskAlertRecord> results = alerts.stream().map(r ->
                new RiskAlertRecord(
                        r.getId(),
                        r.getRiskCategory().name(),
                        r.getSeverityLevel().name(),
                        r.getEntityType(),
                        r.getDescription(),
                        r.getRecommendationText()
                )
        ).toList();
        log.info("[SPRING AI TOOL COMPLETE] getSupplyChainRisks() returned {} risk alerts.", results.size());
        return results;
    }

    public record RiskAlertRecord(
            Long id,
            String category,
            String severity,
            String entityType,
            String description,
            String recommendation
    ) {}
}

