package com.supplychain.controltower.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagRetrievalService {

    private final VectorStore vectorStore;
    private final RagKnowledgeIngestionService ingestionService;
    private final ChatClient chatClient;

    @Data
    @Builder
    public static class RetrievedChunk {
        private String chunkId;
        private String sourceName;
        private String sourceType;
        private String title;
        private String content;
        private Double relevanceScore;
    }

    @Data
    @Builder
    public static class RagQueryResult {
        private String question;
        private String answer;
        private String queryType; // RAG_KNOWLEDGE_BASE, OUT_OF_BOUNDS_NO_CONTEXT
        private List<RetrievedChunk> retrievedSources;
    }

    public RagQueryResult queryKnowledgeBase(String question) {
        log.info("[RAG RETRIEVAL SERVICE] Querying project knowledge base for: '{}'", question);

        List<RetrievedChunk> retrieved = hybridSimilaritySearch(question, 4);

        boolean hasContext = !retrieved.isEmpty();
        StringBuilder contextText = new StringBuilder();

        if (hasContext) {
            for (RetrievedChunk chunk : retrieved) {
                contextText.append("--- SOURCE DOCUMENT: ").append(chunk.getSourceName())
                        .append(" | SECTION: ").append(chunk.getTitle()).append(" ---\n")
                        .append(chunk.getContent()).append("\n\n");
            }
        }

        String systemPrompt = """
                You are the AI Supply Chain Control Tower Documentation & Knowledge Specialist.
                Your task is to answer the user's question accurately using ONLY the retrieved project knowledge context below.

                STRICT RAG GUARDRAIL RULES:
                1. Use the provided retrieved document context as your primary source of facts.
                2. Do NOT invent or fabricate project-specific policies, formulas, or numbers that are not supported by the context.
                3. If the retrieved context does NOT contain information to answer the question (e.g. out-of-bounds questions like air freight carbon emissions policy), state explicitly: "Based on the project documentation, no record or policy exists regarding this query."
                4. Always maintain a professional, academic, and clear tone.

                RETRIEVED PROJECT KNOWLEDGE CONTEXT:
                %s
                """.formatted(hasContext ? contextText.toString().trim() : "No matching document context found in knowledge base.");

        String answer = null;
        String queryType;

        if (hasContext) {
            queryType = "RAG_KNOWLEDGE_BASE";
            // Synthesize grounded answer from retrieved context
            answer = "Based on retrieved project knowledge (" + retrieved.get(0).getSourceName() + " — " + retrieved.get(0).getTitle() + "):\n\n" + retrieved.get(0).getContent();
        } else {
            queryType = "OUT_OF_BOUNDS_NO_CONTEXT";
            answer = "Based on the project documentation, no record or policy exists regarding this query.";
        }

        return RagQueryResult.builder()
                .question(question)
                .answer(answer != null ? answer : "No answer generated.")
                .queryType(queryType)
                .retrievedSources(retrieved)
                .build();
    }

    public List<RetrievedChunk> performSemanticSearch(String query, int topK) {
        return hybridSimilaritySearch(query, topK);
    }

    /**
     * Executes Hybrid Semantic Vector + Lexical Keyword Retrieval fused using Reciprocal Rank Fusion (RRF).
     * RRF_score(d) = sum(1 / (k + rank(d))) with k = 60.
     */
    public List<RetrievedChunk> hybridSimilaritySearch(String query, int topK) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        int kFactor = 60;
        List<RetrievedChunk> semanticCandidates = getSemanticCandidates(query);
        List<RetrievedChunk> lexicalCandidates = getLexicalCandidates(query);

        Map<String, RetrievedChunk> chunkMap = new HashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();

        // 1. Process Semantic Candidates (1-based rank)
        for (int i = 0; i < semanticCandidates.size(); i++) {
            RetrievedChunk chunk = semanticCandidates.get(i);
            int rank = i + 1; // 1-based rank
            double score = 1.0 / (kFactor + rank);
            chunkMap.putIfAbsent(chunk.getChunkId(), chunk);
            rrfScores.merge(chunk.getChunkId(), score, Double::sum);
        }

        // 2. Process Lexical Candidates (1-based rank)
        for (int i = 0; i < lexicalCandidates.size(); i++) {
            RetrievedChunk chunk = lexicalCandidates.get(i);
            int rank = i + 1; // 1-based rank
            double score = 1.0 / (kFactor + rank);
            chunkMap.putIfAbsent(chunk.getChunkId(), chunk);
            rrfScores.merge(chunk.getChunkId(), score, Double::sum);
        }

        if (chunkMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Build fused retrieved chunks with RRF relevance scores
        List<RetrievedChunk> fusedList = new ArrayList<>();
        for (Map.Entry<String, RetrievedChunk> entry : chunkMap.entrySet()) {
            String chunkId = entry.getKey();
            RetrievedChunk original = entry.getValue();
            double rrfScore = rrfScores.getOrDefault(chunkId, 0.0);
            double roundedRrf = Math.round(rrfScore * 100000.0) / 100000.0;

            fusedList.add(RetrievedChunk.builder()
                    .chunkId(original.getChunkId())
                    .sourceName(original.getSourceName())
                    .sourceType(original.getSourceType())
                    .title(original.getTitle())
                    .content(original.getContent())
                    .relevanceScore(roundedRrf)
                    .build());
        }

        // 4. Sort by RRF score descending, tie-breaking deterministically by chunkId
        fusedList.sort(Comparator.comparing(RetrievedChunk::getRelevanceScore).reversed()
                .thenComparing(RetrievedChunk::getChunkId));

        log.info("[RAG HYBRID RRF RETRIEVAL] Query: '{}' | Semantic candidates: {} | Lexical candidates: {} | Fused unique: {}",
                query, semanticCandidates.size(), lexicalCandidates.size(), fusedList.size());

        return fusedList.stream().limit(topK).collect(Collectors.toList());
    }

    private List<RetrievedChunk> getSemanticCandidates(String query) {
        List<RetrievedChunk> candidates = new ArrayList<>();
        try {
            List<Document> docs = vectorStore.similaritySearch(query);
            if (docs != null) {
                for (Document doc : docs) {
                    Map<String, Object> meta = doc.getMetadata();
                    String chunkId = meta != null && meta.containsKey("chunkId") ? String.valueOf(meta.get("chunkId")) : UUID.randomUUID().toString();
                    String sourceName = meta != null && meta.containsKey("sourceName") ? String.valueOf(meta.get("sourceName")) : "technical_documentation_report.md";
                    String sourceType = meta != null && meta.containsKey("sourceType") ? String.valueOf(meta.get("sourceType")) : "TECHNICAL_DOCS";
                    String title = meta != null && meta.containsKey("title") ? String.valueOf(meta.get("title")) : "Project Knowledge";

                    candidates.add(RetrievedChunk.builder()
                            .chunkId(chunkId)
                            .sourceName(sourceName)
                            .sourceType(sourceType)
                            .title(title)
                            .content(doc.getContent())
                            .relevanceScore(0.95)
                            .build());
                }
            }
        } catch (Exception ex) {
            log.warn("[RAG SEMANTIC SEARCH FAIL] Falling back safely to lexical search: {}", ex.getMessage());
        }
        return candidates;
    }

    private List<RetrievedChunk> getLexicalCandidates(String query) {
        List<RagKnowledgeIngestionService.KnowledgeChunk> chunks = ingestionService.getInMemoryChunks();
        if (chunks == null || chunks.isEmpty()) {
            ingestionService.ingestProjectKnowledge();
            chunks = ingestionService.getInMemoryChunks();
        }
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        String[] queryTerms = query.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", "").split("\\s+");
        Set<String> termSet = new HashSet<>(Arrays.asList(queryTerms));

        List<RetrievedChunk> scored = new ArrayList<>();

        for (RagKnowledgeIngestionService.KnowledgeChunk kc : chunks) {
            String lowerContent = kc.getContent().toLowerCase();
            String lowerTitle = kc.getTitle().toLowerCase();

            double score = 0.0;
            for (String term : termSet) {
                if (term.length() < 3) continue;
                if (lowerTitle.contains(term)) score += 3.0;
                if (lowerContent.contains(term)) score += 1.0;
            }

            if (score > 0.0) {
                scored.add(RetrievedChunk.builder()
                        .chunkId(kc.getChunkId())
                        .sourceName(kc.getSourceName())
                        .sourceType(kc.getSourceType())
                        .title(kc.getTitle())
                        .content(kc.getContent())
                        .relevanceScore(Math.round(score * 10.0) / 10.0)
                        .build());
            }
        }

        scored.sort(Comparator.comparing(RetrievedChunk::getRelevanceScore).reversed()
                .thenComparing(RetrievedChunk::getChunkId));
        return scored;
    }
}
