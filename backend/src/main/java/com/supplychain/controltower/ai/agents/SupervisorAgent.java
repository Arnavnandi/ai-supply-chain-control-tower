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

        for (String domain : selectedDomains) {
            futures.add(CompletableFuture.supplyAsync(() -> executeDomainAgentSafely(domain, prompt))
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
        String supervisorSynthesis = generateSupervisorSynthesis(prompt, selectedDomains, findings, offlineFallback);

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
                    return DomainFinding.builder()
                            .domainAgent("INVENTORY")
                            .findingSummary(invResp)
                            .riskImpactScore(invResp.contains("Stockout Risk Items: `0`") ? 15.0 : 75.0)
                            .recommendedAction("Expedite purchase orders for low-stock SKUs below safety thresholds.")
                            .fallbackUsed(invResp.contains("FALLBACK") || invResp.contains("Analyzed live inventory"))
                            .build();

                case "SUPPLIER":
                    String supResp = supplierAgent.processQuery(prompt);
                    return DomainFinding.builder()
                            .domainAgent("SUPPLIER")
                            .findingSummary(supResp)
                            .riskImpactScore(supResp.contains("HIGH_RISK") ? 80.0 : 30.0)
                            .recommendedAction("Negotiate lead time SLA guarantees or switch to backup vendor contracts.")
                            .fallbackUsed(supResp.contains("FALLBACK") || supResp.contains("Evaluated live supplier"))
                            .build();

                case "LOGISTICS":
                    String logResp = logisticsAgent.processQuery(prompt);
                    return DomainFinding.builder()
                            .domainAgent("LOGISTICS")
                            .findingSummary(logResp)
                            .riskImpactScore(logResp.contains("Delayed Transit Shipments: `0`") ? 10.0 : 70.0)
                            .recommendedAction("Reroute delayed transit shipments to priority air cargo carriers.")
                            .fallbackUsed(logResp.contains("FALLBACK") || logResp.contains("Tracked active shipments"))
                            .build();

                case "WAREHOUSE":
                    String whResp = warehouseAgent.processQuery(prompt);
                    return DomainFinding.builder()
                            .domainAgent("WAREHOUSE")
                            .findingSummary(whResp)
                            .riskImpactScore(25.0)
                            .recommendedAction("Rebalance regional stock distribution across secondary warehouse hubs.")
                            .fallbackUsed(whResp.contains("FALLBACK") || whResp.contains("Monitored regional distribution"))
                            .build();

                case "RISK":
                    String riskResp = riskAgent.processQuery(prompt);
                    return DomainFinding.builder()
                            .domainAgent("RISK")
                            .findingSummary(riskResp)
                            .riskImpactScore(65.0)
                            .recommendedAction("Execute immediate multi-factor risk mitigation workflow.")
                            .fallbackUsed(riskResp.contains("FALLBACK") || riskResp.contains("Operational Risk Analysis"))
                            .build();

                case "RAG":
                    var ragResult = ragRetrievalService.queryKnowledgeBase(prompt);
                    return DomainFinding.builder()
                            .domainAgent("RAG")
                            .findingSummary("Retrieved Grounded Project Knowledge: " + ragResult.getAnswer())
                            .riskImpactScore(10.0)
                            .recommendedAction("Verify operational procedures against grounded technical documentation.")
                            .fallbackUsed(false)
                            .build();

                default:
                    return DomainFinding.builder()
                            .domainAgent(domain)
                            .findingSummary("Generic domain evaluation completed.")
                            .riskImpactScore(20.0)
                            .recommendedAction("Monitor domain metrics.")
                            .fallbackUsed(true)
                            .build();
            }
        } catch (Exception ex) {
            log.warn("[DOMAIN AGENT FAIL] Domain '{}' execution error: {}", domain, ex.getMessage());
            return DomainFinding.builder()
                    .domainAgent(domain)
                    .findingSummary("Domain telemetry execution error: " + ex.getMessage())
                    .riskImpactScore(50.0)
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

    private String generateSupervisorSynthesis(String prompt, List<String> domains, List<DomainFinding> findings, boolean fallbackActive) {
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
                                Highlight cross-domain risk correlations and give clear prioritized mitigation steps.
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
        sb.append("1. **Priority 1**: Immediate replenishment order execution for critical stockout items.\n");
        sb.append("2. **Priority 2**: Carrier rerouting for delayed logistics shipments.\n");
        sb.append("3. **Priority 3**: Vendor OTIF performance review for high-risk suppliers.\n");

        return sb.toString();
    }
}
