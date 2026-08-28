package com.supplychain.controltower.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagKnowledgeIngestionService implements CommandLineRunner {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    private static final List<String> SAFE_KNOWLEDGE_FILES = List.of(
            "technical_documentation_report.md",
            "README.md",
            "walkthrough.md"
    );

    @Data
    @Builder
    public static class KnowledgeChunk {
        private String chunkId;
        private String sourceName;
        private String sourceType; // TECHNICAL_DOCS, README, WALKTHROUGH
        private String title;
        private String content;
        private Map<String, Object> metadata;
    }

    private final List<KnowledgeChunk> inMemoryChunks = new ArrayList<>();

    @Override
    public void run(String... args) {
        log.info("[RAG INGESTION SERVICE] Ingesting safe project knowledge documentation...");
        ingestProjectKnowledge();
    }

    public synchronized int ingestProjectKnowledge() {
        inMemoryChunks.clear();
        int totalChunks = 0;

        List<Path> candidatePaths = new ArrayList<>();

        // 1. Try Classpath resources / docs directory
        try {
            org.springframework.core.io.support.PathMatchingResourcePatternResolver resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
            org.springframework.core.io.Resource[] resources = resolver.getResources("classpath:docs/*.md");
            for (org.springframework.core.io.Resource res : resources) {
                if (res.exists()) {
                    String fileName = res.getFilename();
                    String text = new String(res.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    List<KnowledgeChunk> chunks = chunkMarkdownDocument(fileName, text);
                    inMemoryChunks.addAll(chunks);
                    totalChunks += chunks.size();

                    List<Document> aiDocs = new ArrayList<>();
                    for (KnowledgeChunk kc : chunks) {
                        aiDocs.add(new Document(kc.getContent(), kc.getMetadata()));
                    }
                    try {
                        vectorStore.add(aiDocs);
                        log.info("[RAG VECTORSTORE] Indexed {} chunks with dense vector embeddings into PgVectorStore.", aiDocs.size());
                    } catch (Exception ex) {
                        log.warn("[RAG VECTORSTORE EXCEPTION] VectorStore add warning: {}", ex.getMessage());
                        persistChunksToPostgresVectorStore(chunks);
                    }
                }
            }
            if (totalChunks > 0) {
                log.info("[RAG INGESTION COMPLETED] Loaded {} knowledge chunks from Classpath docs resources.", totalChunks);
                return totalChunks;
            }
        } catch (Exception e) {
            log.warn("[RAG CLASSPATH LOAD WARNING] Failed to read classpath docs: {}", e.getMessage());
        }

        // 2. Fallback to local filesystem paths
        String brainPath = "/Users/arnavnandi/.gemini/antigravity-ide/brain/8ee0a1e0-19c7-47ba-a636-016a310b6042";
        String rootPath = "/Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower";

        for (String fileName : SAFE_KNOWLEDGE_FILES) {
            Path pBrain = Paths.get(brainPath, fileName);
            if (Files.exists(pBrain)) {
                candidatePaths.add(pBrain);
            }
            Path pRoot = Paths.get(rootPath, fileName);
            if (Files.exists(pRoot) && !candidatePaths.contains(pRoot)) {
                candidatePaths.add(pRoot);
            }
        }

        for (Path filePath : candidatePaths) {
            try {
                String fileName = filePath.getFileName().toString();
                if (fileName.contains(".env") || fileName.contains("secret") || fileName.contains("key") || fileName.endsWith(".log")) {
                    log.warn("[RAG INGESTION] Skipping potentially sensitive file: {}", fileName);
                    continue;
                }

                String text = Files.readString(filePath);
                List<KnowledgeChunk> chunks = chunkMarkdownDocument(fileName, text);

                inMemoryChunks.addAll(chunks);
                totalChunks += chunks.size();

                // Index into Spring AI VectorStore
                List<Document> aiDocs = new ArrayList<>();
                for (KnowledgeChunk kc : chunks) {
                    aiDocs.add(new Document(kc.getContent(), kc.getMetadata()));
                }

                try {
                    java.util.concurrent.CompletableFuture.runAsync(() -> vectorStore.add(aiDocs));
                } catch (Exception ex) {
                    log.info("[RAG VECTORSTORE] Spring AI VectorStore background indexing task initiated.");
                }

            } catch (Exception e) {
                log.error("[RAG INGESTION ERROR] Failed to ingest file: {}", filePath, e);
            }
        }

        persistChunksToPostgresVectorStore(inMemoryChunks);

        log.info("[RAG INGESTION COMPLETED] Ingested total {} knowledge chunks across {} sources.",
                inMemoryChunks.size(), candidatePaths.size());
        return inMemoryChunks.size();
    }

    private void persistChunksToPostgresVectorStore(List<KnowledgeChunk> chunks) {
        if (jdbcTemplate == null || chunks == null || chunks.isEmpty()) return;
        try {
            jdbcTemplate.execute("DELETE FROM vector_store WHERE metadata->>'sourceName' IN ('technical_documentation_report.md', 'README.md', 'walkthrough.md')");
            String sql = "INSERT INTO vector_store (id, content, metadata) VALUES (gen_random_uuid(), ?, ?::json)";
            int inserted = 0;
            for (KnowledgeChunk kc : chunks) {
                String safeTitle = kc.getTitle() != null ? kc.getTitle().replace("\"", "'").replace("\n", " ") : "";
                String safeSource = kc.getSourceName() != null ? kc.getSourceName() : "";
                String safeType = kc.getSourceType() != null ? kc.getSourceType() : "";
                String safeChunkId = kc.getChunkId() != null ? kc.getChunkId() : "";

                String metaJson = String.format("{\"chunkId\":\"%s\",\"sourceName\":\"%s\",\"sourceType\":\"%s\",\"title\":\"%s\"}",
                        safeChunkId, safeSource, safeType, safeTitle);
                jdbcTemplate.update(sql, kc.getContent(), metaJson);
                inserted++;
            }
            log.info("[RAG POSTGRES STORE] Persisted {} knowledge chunk records directly into PostgreSQL vector_store table.", inserted);
        } catch (Exception ex) {
            log.warn("[RAG POSTGRES STORE EXCEPTION] Failed to persist chunks to vector_store: {}", ex.getMessage());
        }
    }

    public List<KnowledgeChunk> getInMemoryChunks() {
        return Collections.unmodifiableList(inMemoryChunks);
    }

    private List<KnowledgeChunk> chunkMarkdownDocument(String fileName, String text) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        String[] sections = text.split("(?=\n## )|(?=\n# )");
        int index = 0;

        String sourceType = fileName.contains("documentation") ? "TECHNICAL_DOCS" :
                fileName.contains("walkthrough") ? "WALKTHROUGH" : "README";

        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.length() < 30) continue;

            // Extract heading if present
            String title = fileName;
            if (trimmed.startsWith("#")) {
                int lineBreak = trimmed.indexOf("\n");
                if (lineBreak > 0) {
                    title = trimmed.substring(0, lineBreak).replaceAll("^#+\\s*", "").trim();
                } else {
                    title = trimmed.replaceAll("^#+\\s*", "").trim();
                }
            }

            // Further split if section is large (>1200 chars)
            if (trimmed.length() > 1200) {
                int length = trimmed.length();
                int subIndex = 0;
                for (int i = 0; i < length; i += 800) {
                    String subContent = trimmed.substring(i, Math.min(length, i + 800)).trim();
                    if (subContent.length() < 30) continue;

                    String chunkId = fileName + "-chunk-" + index + "-" + subIndex;
                    Map<String, Object> meta = Map.of(
                            "chunkId", chunkId,
                            "sourceName", fileName,
                            "sourceType", sourceType,
                            "title", title
                    );

                    chunks.add(KnowledgeChunk.builder()
                            .chunkId(chunkId)
                            .sourceName(fileName)
                            .sourceType(sourceType)
                            .title(title + " (Part " + (subIndex + 1) + ")")
                            .content(subContent)
                            .metadata(meta)
                            .build());
                    subIndex++;
                }
            } else {
                String chunkId = fileName + "-chunk-" + index;
                Map<String, Object> meta = Map.of(
                        "chunkId", chunkId,
                        "sourceName", fileName,
                        "sourceType", sourceType,
                        "title", title
                );

                chunks.add(KnowledgeChunk.builder()
                        .chunkId(chunkId)
                        .sourceName(fileName)
                        .sourceType(sourceType)
                        .title(title)
                        .content(trimmed)
                        .metadata(meta)
                        .build());
            }

            index++;
        }

        return chunks;
    }
}
