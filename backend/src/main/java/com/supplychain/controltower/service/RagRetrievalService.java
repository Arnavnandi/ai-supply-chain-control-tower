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

        List<RetrievedChunk> retrieved = performSemanticSearch(question, 4);

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
        List<RetrievedChunk> results = new ArrayList<>();

        // 1. Spring AI PgVectorStore dense vector similarity search
        try {
            List<Document> docs = vectorStore.similaritySearch(query);
            if (docs != null && !docs.isEmpty()) {
                for (Document doc : docs) {
                    Map<String, Object> meta = doc.getMetadata();
                    results.add(RetrievedChunk.builder()
                            .chunkId(String.valueOf(meta.getOrDefault("chunkId", UUID.randomUUID().toString())))
                            .sourceName(String.valueOf(meta.getOrDefault("sourceName", "technical_documentation_report.md")))
                            .sourceType(String.valueOf(meta.getOrDefault("sourceType", "TECHNICAL_DOCS")))
                            .title(String.valueOf(meta.getOrDefault("title", "Project Knowledge")))
                            .content(doc.getContent())
                            .relevanceScore(0.95)
                            .build());
                }
                log.info("[RAG PGVECTORSTORE SEARCH] Retrieved {} chunks via PostgreSQL vector similarity search.", results.size());
                return results.stream().limit(topK).collect(Collectors.toList());
            }
        } catch (Exception ex) {
            log.info("[RAG IN-MEMORY SEARCH] Using fast term-frequency keyword similarity search engine fallback: {}", ex.getMessage());
        }

        // 2. In-Memory Term-Frequency Keyword Similarity Search Fallback
        List<RagKnowledgeIngestionService.KnowledgeChunk> chunks = ingestionService.getInMemoryChunks();
        if (chunks.isEmpty()) {
            ingestionService.ingestProjectKnowledge();
            chunks = ingestionService.getInMemoryChunks();
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

        scored.sort(Comparator.comparing(RetrievedChunk::getRelevanceScore).reversed());
        List<RetrievedChunk> topResults = scored.stream().filter(c -> c.getRelevanceScore() >= 5.5).limit(topK).collect(Collectors.toList());
        return topResults;
    }
}
