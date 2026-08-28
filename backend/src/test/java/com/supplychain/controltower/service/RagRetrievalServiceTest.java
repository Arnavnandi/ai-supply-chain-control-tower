package com.supplychain.controltower.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
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

    private RagKnowledgeIngestionService.KnowledgeChunk sampleChunk;

    @BeforeEach
    void setUp() {
        sampleChunk = RagKnowledgeIngestionService.KnowledgeChunk.builder()
                .chunkId("chunk-1")
                .sourceName("technical_documentation_report.md")
                .sourceType("TECHNICAL_DOCS")
                .title("Safety Stock Formula")
                .content("Safety Stock formula is SS = Z * sigma_d * sqrt(L)")
                .build();
    }

    @Test
    void performSemanticSearch_ShouldReturnMatchingChunks() {
        when(ingestionService.getInMemoryChunks()).thenReturn(List.of(sampleChunk));

        List<RagRetrievalService.RetrievedChunk> results = ragRetrievalService.performSemanticSearch("safety stock formula", 3);

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals("technical_documentation_report.md", results.get(0).getSourceName());
        assertTrue(results.get(0).getContent().contains("SS = Z * sigma_d * sqrt(L)"));
    }
}
