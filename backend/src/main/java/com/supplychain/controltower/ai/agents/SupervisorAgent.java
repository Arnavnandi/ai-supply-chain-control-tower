package com.supplychain.controltower.ai.agents;

import com.supplychain.controltower.service.RagRetrievalService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupervisorAgent {

    private final InventoryAgent inventoryAgent;
    private final SupplierAgent supplierAgent;
    private final LogisticsAgent logisticsAgent;
    private final WarehouseAgent warehouseAgent;
    private final RiskAgent riskAgent;
    private final RagRetrievalService ragRetrievalService;
    private final ChatClient chatClient;

    @Data
    @Builder
    public static class DomainFinding {
        private String domainAgent;
        private String findingSummary;
        private Double riskImpactScore;
        private String recommendedAction;
        private boolean fallbackUsed;
    }

    @Data
    @Builder
    public static class SupervisorConsensusResponse {
        private String originalQuery;
        private String supervisorSynthesis;
        private String consensusDecision;
        private Double overallSystemRiskScore;
        private List<String> participatingAgents;
        private List<DomainFinding> domainFindings;
        private List<String> prioritizedMitigationActions;
        private boolean offlineFallbackActive;
        private String timestamp;
    }

    public SupervisorConsensusResponse processMultiAgentQuery(String prompt) {
        log.info("[SUPERVISOR AGENT INITIATED] Processing multi-agent collaborative query: '{}'", prompt);

        List<String> selectedDomains = selectRelevantDomains(prompt);
        log.info("[SUPERVISOR AGENT DELEGATION] Selected domain agents for collaboration: {}", selectedDomains);

        List<CompletableFuture<DomainFinding>> futures = new ArrayList<>();

        Long callerCompanyId = com.supplychain.controltower.security.TenantContext.getRequiredCompanyId();

        for (String domain : selectedDomains) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                com.supplychain.controltower.security.TenantContext.setCompanyId(callerCompanyId);
                try {
                    return executeDomainAgentSafely(domain, prompt);
                } finally {
                    com.supplychain.controltower.security.TenantContext.clear();
                }
            })
                    .orTimeout(3, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        log.warn("[SUPERVISOR AGENT TIMEOUT/FAIL] Domain agent '{}' failed: {}", domain, ex.getMessage());
                        return DomainFinding.builder()
                                .domainAgent(domain)
                                .findingSummary("Domain telemetry analysis unavailable: " + ex.getMessage())
                                .riskImpactScore(0.0)
                                .recommendedAction("Inspect service logs for " + domain)
                                .fallbackUsed(true)
                                .build();
                    }));
        }

        List<DomainFinding> findings = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        boolean offlineFallback = findings.stream().anyMatch(DomainFinding::isFallbackUsed);

        // Synthesize Consensus Decision & Risk Score
        double overallRiskScore = calculateOverallRiskScore(findings);
        String consensusDecision = determineConsensusDecision(overallRiskScore, findings);
        List<String> prioritizedActions = generatePrioritizedMitigationActions(findings);
        String supervisorSynthesis = generateSupervisorSynthesis(prompt, selectedDomains, findings, offlineFallback, prioritizedActions);

        return SupervisorConsensusResponse.builder()
                .originalQuery(prompt)
                .supervisorSynthesis(supervisorSynthesis)
                .consensusDecision(consensusDecision)
                .overallSystemRiskScore(overallRiskScore)
                .participatingAgents(selectedDomains)
                .domainFindings(findings)
                .prioritizedMitigationActions(prioritizedActions)
                .offlineFallbackActive(offlineFallback)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    private List<String> selectRelevantDomains(String prompt) {
        String lower = prompt != null ? prompt.toLowerCase() : "";
        Set<String> domains = new LinkedHashSet<>();

        if (lower.contains("stock") || lower.contains("inventory") || lower.contains("sku") || lower.contains("reorder") || lower.contains("safety")) {
            domains.add("INVENTORY");
        }
        if (lower.contains("supplier") || lower.contains("vendor") || lower.contains("lead time") || lower.contains("otif") || lower.contains("price")) {
            domains.add("SUPPLIER");
        }
        if (lower.contains("shipment") || lower.contains("delay") || lower.contains("logistics") || lower.contains("tracking") || lower.contains("carrier")) {
            domains.add("LOGISTICS");
        }
        if (lower.contains("warehouse") || lower.contains("capacity") || lower.contains("facility") || lower.contains("hub")) {
            domains.add("WAREHOUSE");
        }
        if (lower.contains("risk") || lower.contains("alert") || lower.contains("disruption") || lower.contains("overall")) {
            domains.add("RISK");
        }
        if (lower.contains("formula") || lower.contains("policy") || lower.contains("documentation") || lower.contains("architecture")) {
            domains.add("RAG");
        }

        if (domains.isEmpty()) {
            domains.add("INVENTORY");
            domains.add("SUPPLIER");
            domains.add("LOGISTICS");
            domains.add("RISK");
        }

        return new ArrayList<>(domains);
    }

    private DomainFinding executeDomainAgentSafely(String domain, String prompt) {
        try {
            switch (domain) {
                case "INVENTORY":
                    String invResp = inventoryAgent.processQuery(prompt);
                    boolean invNoData = invResp.contains("No operational inventory records found");
                    return DomainFinding.builder()
                            .domainAgent("INVENTORY")
                            .findingSummary(invResp)
                            .riskImpactScore(invNoData ? 0.0 : (invResp.contains("Stockout Risk Items: `0`") ? 10.0 : 75.0))
                            .recommendedAction(invNoData ? "Upload inventory CSV to enable stock monitoring." : (invResp.contains("Stockout Risk Items: `0`") ? null : "Expedite purchase orders for low-stock SKUs."))
                            .fallbackUsed(invResp.contains("FALLBACK") || invResp.contains("Analyzed live inventory"))
                            .build();

                case "SUPPLIER":
                    String supResp = supplierAgent.processQuery(prompt);
                    boolean supNoData = supResp.contains("No supplier records found");
                    return DomainFinding.builder()
                            .domainAgent("SUPPLIER")
                            .findingSummary(supResp)
                            .riskImpactScore(supNoData ? 0.0 : (supResp.contains("HIGH_RISK") ? 80.0 : 25.0))
                            .recommendedAction(supNoData ? "Upload supplier CSV to monitor vendor performance." : "Negotiate SLA guarantees or review backup vendor contracts.")
                            .fallbackUsed(supResp.contains("FALLBACK") || supResp.contains("Evaluated live supplier"))
                            .build();

                case "LOGISTICS":
                    String logResp = logisticsAgent.processQuery(prompt);
                    boolean logNoData = logResp.contains("No shipment records found");
                    return DomainFinding.builder()
                            .domainAgent("LOGISTICS")
                            .findingSummary(logResp)
                            .riskImpactScore(logNoData ? 0.0 : (logResp.contains("Delayed Transit Shipments: `0`") || logResp.contains("operating on-schedule") ? 10.0 : 70.0))
                            .recommendedAction(logNoData ? "Upload shipment CSV to track logistics." : (logResp.contains("Delayed Transit Shipments: `0`") || logResp.contains("operating on-schedule") ? null : "Reroute delayed transit shipments to priority air cargo."))
                            .fallbackUsed(logResp.contains("FALLBACK") || logResp.contains("Tracked active shipments"))
                            .build();

                case "WAREHOUSE":
                    String whResp = warehouseAgent.processQuery(prompt);
                    boolean whNoData = whResp.contains("No warehouse records found");
                    return DomainFinding.builder()
                            .domainAgent("WAREHOUSE")
                            .findingSummary(whResp)
                            .riskImpactScore(whNoData ? 0.0 : 20.0)
                            .recommendedAction(whNoData ? "Upload warehouse CSV to monitor storage utilization." : null)
                            .fallbackUsed(whResp.contains("FALLBACK") || whResp.contains("Storage capacity"))
                            .build();

                case "RISK":
                    String riskResp = riskAgent.processQuery(prompt);
                    boolean riskNoData = riskResp.contains("No active risk alerts recorded. Operational records are unpopulated");
                    return DomainFinding.builder()
                            .domainAgent("RISK")
                            .findingSummary(riskResp)
                            .riskImpactScore(riskNoData ? 0.0 : 50.0)
                            .recommendedAction(riskNoData ? "Upload operational datasets to activate risk detection." : "Review active risk alerts and initiate mitigation workflows.")
                            .fallbackUsed(riskResp.contains("FALLBACK") || riskResp.contains("Operational Risk Analysis"))
                            .build();

                case "RAG":
                    var ragResult = ragRetrievalService.queryKnowledgeBase(prompt);
                    return DomainFinding.builder()
                            .domainAgent("RAG")
                            .findingSummary("Retrieved Grounded Project Knowledge: " + ragResult.getAnswer())
                            .riskImpactScore(0.0)
                            .recommendedAction(null)
                            .fallbackUsed(false)
                            .build();

                default:
                    return DomainFinding.builder()
                            .domainAgent(domain)
                            .findingSummary("Generic domain evaluation completed.")
                            .riskImpactScore(0.0)
                            .recommendedAction(null)
                            .fallbackUsed(true)
                            .build();
            }
        } catch (Exception ex) {
            log.warn("[DOMAIN AGENT FAIL] Domain '{}' execution error: {}", domain, ex.getMessage());
            return DomainFinding.builder()
                    .domainAgent(domain)
                    .findingSummary("Domain telemetry execution error: " + ex.getMessage())
                    .riskImpactScore(0.0)
                    .recommendedAction("Review service health for " + domain)
                    .fallbackUsed(true)
                    .build();
        }
    }

    private double calculateOverallRiskScore(List<DomainFinding> findings) {
        if (findings == null || findings.isEmpty()) return 0.0;
        double totalScore = findings.stream().mapToDouble(DomainFinding::getRiskImpactScore).sum();
        double avg = totalScore / findings.size();
        return Math.round(avg * 10.0) / 10.0;
    }

    private String determineConsensusDecision(double overallRiskScore, List<DomainFinding> findings) {
        if (overallRiskScore >= 60.0) {
            return "EXPEDITED_REPLENISHMENT_AND_REROUTE";
        } else if (overallRiskScore >= 35.0) {
            return "SUPPLIER_NEGOTIATION_AND_MONITORING";
        } else {
            return "NORMAL_OPERATIONAL_CONTINUITY";
        }
    }

    private List<String> generatePrioritizedMitigationActions(List<DomainFinding> findings) {
        List<String> actions = new ArrayList<>();
        List<DomainFinding> sorted = new ArrayList<>(findings);
        sorted.sort(Comparator.comparing(DomainFinding::getRiskImpactScore).reversed());

        for (DomainFinding f : sorted) {
            if (f.getRecommendedAction() != null && !f.getRecommendedAction().isBlank()) {
                actions.add("[" + f.getDomainAgent() + "] " + f.getRecommendedAction());
            }
        }
        return actions;
    }

    private String generateSupervisorSynthesis(String prompt, List<String> domains, List<DomainFinding> findings, boolean fallbackActive, List<String> prioritizedActions) {
        String apiKey = System.getenv("GEMINI_API_KEY");
        boolean validKey = apiKey != null && !apiKey.isBlank() && !"unconfigured".equalsIgnoreCase(apiKey) && !apiKey.contains("your-api-key");
        try {
            if (!fallbackActive && validKey) {
                StringBuilder context = new StringBuilder();
                for (DomainFinding f : findings) {
                    context.append("Domain Agent ").append(f.getDomainAgent()).append(" Findings:\n")
                            .append(f.getFindingSummary()).append("\n\n");
                }

                return chatClient.prompt()
                        .system("""
                                You are the Chief Supply Chain Supervisor Agent.
                                Your job is synthesizing multi-agent findings from Inventory, Supplier, Logistics, Warehouse, and Risk agents into a unified Executive Consensus Briefing.
                                Ground all statements strictly in the provided multi-agent telemetry and database records.
                                Never invent numbers, suppliers, or products.
                                """)
                        .user("User Query: " + prompt + "\n\nMulti-Agent Telemetry:\n" + context.toString())
                        .call()
                        .content();
            }
        } catch (Exception ex) {
            log.warn("[SUPERVISOR SYNTHESIS FALLBACK] LLM synthesis unavailable, using data-grounded synthesis: {}", ex.getMessage());
        }

        // Data-Grounded Deterministic Executive Consensus Synthesis Fallback
        StringBuilder sb = new StringBuilder();
        sb.append("### 🛸 Executive Multi-Agent Collaborative Control Tower Synthesis\n");
        sb.append("Synthesized telemetry from ").append(domains.size()).append(" collaborating domain agent(s): `")
                .append(String.join("`, `", domains)).append("`\n\n");

        for (DomainFinding f : findings) {
            sb.append("#### 🔹 ").append(f.getDomainAgent()).append(" Agent Telemetry\n");
            sb.append(f.getFindingSummary()).append("\n\n");
        }

        sb.append("#### ⚡ Cross-Domain Consensus & Action Plan\n");
        if (prioritizedActions.isEmpty()) {
            sb.append("No immediate intervention required. Supply chain operations are within normal parameters or awaiting operational data upload.\n");
        } else {
            for (int i = 0; i < prioritizedActions.size(); i++) {
                sb.append(i + 1).append(". ").append(prioritizedActions.get(i)).append("\n");
            }
        }

        return sb.toString();
    }
}
