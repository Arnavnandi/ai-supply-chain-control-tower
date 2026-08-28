package com.supplychain.controltower.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private RagKnowledgeIngestionService ingestionService;

    @Mock
    private ChatClient chatClient;

    @InjectMocks
    private RagRetrievalService ragRetrievalService;

    private RagKnowledgeIngestionService.KnowledgeChunk sampleChunk1;
    private RagKnowledgeIngestionService.KnowledgeChunk sampleChunk2;
    private Document sampleDoc1;
    private Document sampleDoc3;

    @BeforeEach
    void setUp() {
        sampleChunk1 = RagKnowledgeIngestionService.KnowledgeChunk.builder()
                .chunkId("chunk-1")
                .sourceName("technical_documentation_report.md")
                .sourceType("TECHNICAL_DOCS")
                .title("Safety Stock Formula")
                .content("Safety Stock formula is SS = Z * sigma_d * sqrt(L)")
                .build();

        sampleChunk2 = RagKnowledgeIngestionService.KnowledgeChunk.builder()
                .chunkId("chunk-2")
                .sourceName("technical_documentation_report.md")
                .sourceType("TECHNICAL_DOCS")
                .title("Logistics Lead Time")
                .content("Lead time L measured in months for supplier orders")
                .build();

        sampleDoc1 = new Document("Safety Stock formula is SS = Z * sigma_d * sqrt(L)",
                Map.of("chunkId", "chunk-1", "title", "Safety Stock Formula", "sourceName", "technical_documentation_report.md"));

        sampleDoc3 = new Document("Demand forecasting uses WMA and Exponential Smoothing",
                Map.of("chunkId", "chunk-3", "title", "Forecasting Engine", "sourceName", "technical_documentation_report.md"));
    }

    @Test
    void hybridSimilaritySearch_CombinedRrfScore_WhenChunkInBothSemanticAndLexical() {
        when(vectorStore.similaritySearch(anyString())).thenReturn(List.of(sampleDoc1));
        when(ingestionService.getInMemoryChunks()).thenReturn(List.of(sampleChunk1));

        List<RagRetrievalService.RetrievedChunk> results = ragRetrievalService.hybridSimilaritySearch("safety stock formula", 3);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("chunk-1", results.get(0).getChunkId());
        // Rank 1 in Semantic (1/61) + Rank 1 in Lexical (1/61) = 2/61 = ~0.03279
        assertEquals(0.03279, results.get(0).getRelevanceScore(), 0.0001);
    }

    @Test
    void hybridSimilaritySearch_SemanticOnlyContribution() {
        when(vectorStore.similaritySearch(anyString())).thenReturn(List.of(sampleDoc3));
        when(ingestionService.getInMemoryChunks()).thenReturn(Collections.emptyList());

        List<RagRetrievalService.RetrievedChunk> results = ragRetrievalService.hybridSimilaritySearch("forecasting", 3);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("chunk-3", results.get(0).getChunkId());
        // Rank 1 in Semantic only = 1/61 = ~0.01639
        assertEquals(0.01639, results.get(0).getRelevanceScore(), 0.0001);
    }

    @Test
    void hybridSimilaritySearch_LexicalOnlyContribution() {
        when(vectorStore.similaritySearch(anyString())).thenReturn(Collections.emptyList());
        when(ingestionService.getInMemoryChunks()).thenReturn(List.of(sampleChunk2));

        List<RagRetrievalService.RetrievedChunk> results = ragRetrievalService.hybridSimilaritySearch("lead time", 3);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("chunk-2", results.get(0).getChunkId());
        // Rank 1 in Lexical only = 1/61 = ~0.01639
        assertEquals(0.01639, results.get(0).getRelevanceScore(), 0.0001);
    }

    @Test
    void hybridSimilaritySearch_DeduplicationAndSorting() {
        // Semantic: doc3 (rank 1), doc1 (rank 2)
        when(vectorStore.similaritySearch(anyString())).thenReturn(List.of(sampleDoc3, sampleDoc1));
        // Lexical: chunk1 (rank 1), chunk2 (rank 2)
        when(ingestionService.getInMemoryChunks()).thenReturn(List.of(sampleChunk1, sampleChunk2));

        List<RagRetrievalService.RetrievedChunk> results = ragRetrievalService.hybridSimilaritySearch("safety stock formula lead time", 10);

        assertNotNull(results);
        assertEquals(3, results.size());

        // chunk-1: Rank 2 Semantic (1/62) + Rank 1 Lexical (1/61) = 0.016129 + 0.016393 = 0.03252
        // chunk-3: Rank 1 Semantic (1/61) = 0.01639
        // chunk-2: Rank 2 Lexical (1/62) = 0.01613
        assertEquals("chunk-1", results.get(0).getChunkId());
        assertEquals("chunk-3", results.get(1).getChunkId());
        assertEquals("chunk-2", results.get(2).getChunkId());
    }

    @Test
    void hybridSimilaritySearch_TopK_Respected() {
        when(vectorStore.similaritySearch(anyString())).thenReturn(List.of(sampleDoc3, sampleDoc1));
        when(ingestionService.getInMemoryChunks()).thenReturn(List.of(sampleChunk1, sampleChunk2));

        List<RagRetrievalService.RetrievedChunk> results = ragRetrievalService.hybridSimilaritySearch("safety stock formula", 2);

        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    void hybridSimilaritySearch_EmptySemanticResults_HandledSafely() {
        when(vectorStore.similaritySearch(anyString())).thenReturn(Collections.emptyList());
        when(ingestionService.getInMemoryChunks()).thenReturn(List.of(sampleChunk1));

        List<RagRetrievalService.RetrievedChunk> results = ragRetrievalService.hybridSimilaritySearch("safety stock", 3);

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals("chunk-1", results.get(0).getChunkId());
    }

    @Test
    void hybridSimilaritySearch_EmptyLexicalResults_HandledSafely() {
        when(vectorStore.similaritySearch(anyString())).thenReturn(List.of(sampleDoc1));
        when(ingestionService.getInMemoryChunks()).thenReturn(Collections.emptyList());

        List<RagRetrievalService.RetrievedChunk> results = ragRetrievalService.hybridSimilaritySearch("safety stock", 3);

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals("chunk-1", results.get(0).getChunkId());
    }

    @Test
    void hybridSimilaritySearch_VectorStoreException_ActivatesSafeFallback() {
        when(vectorStore.similaritySearch(anyString())).thenThrow(new RuntimeException("Database connection timeout"));
        when(ingestionService.getInMemoryChunks()).thenReturn(List.of(sampleChunk1));

        List<RagRetrievalService.RetrievedChunk> results = ragRetrievalService.hybridSimilaritySearch("safety stock formula", 3);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("chunk-1", results.get(0).getChunkId());
    }

    @Test
    void hybridSimilaritySearch_DeterministicTieBreaking() {
        Document docA = new Document("Test content A", Map.of("chunkId", "chunk-A", "title", "Title A"));
        Document docB = new Document("Test content B", Map.of("chunkId", "chunk-B", "title", "Title B"));

        // Both docs have equal rank (Semantic rank 1 vs 2, Lexical rank 2 vs 1) -> equal sum score 1/61 + 1/62
        when(vectorStore.similaritySearch(anyString())).thenReturn(List.of(docA, docB));

        RagKnowledgeIngestionService.KnowledgeChunk chunkB = RagKnowledgeIngestionService.KnowledgeChunk.builder()
                .chunkId("chunk-B").title("Title B").content("Test content B").build();
        RagKnowledgeIngestionService.KnowledgeChunk chunkA = RagKnowledgeIngestionService.KnowledgeChunk.builder()
                .chunkId("chunk-A").title("Title A").content("Test content A").build();
        when(ingestionService.getInMemoryChunks()).thenReturn(List.of(chunkB, chunkA));

        List<RagRetrievalService.RetrievedChunk> results = ragRetrievalService.hybridSimilaritySearch("test content", 5);

        assertNotNull(results);
        assertEquals(2, results.size());
        // Scores are equal (0.03252). Alphabetical tie breaker on chunkId ("chunk-A" before "chunk-B")
        assertEquals("chunk-A", results.get(0).getChunkId());
        assertEquals("chunk-B", results.get(1).getChunkId());
    }
}
