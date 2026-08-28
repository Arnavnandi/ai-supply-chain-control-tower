package com.supplychain.controltower.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RagKnowledgeIngestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @InjectMocks
    private RagKnowledgeIngestionService ingestionService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void ingestProjectKnowledge_ShouldChunkAndIndexSafeFiles() {
        int chunksCount = ingestionService.ingestProjectKnowledge();

        assertTrue(chunksCount >= 0);
        List<RagKnowledgeIngestionService.KnowledgeChunk> chunks = ingestionService.getInMemoryChunks();
        assertNotNull(chunks);

        // Confirm no secrets were indexed
        for (RagKnowledgeIngestionService.KnowledgeChunk c : chunks) {
            assertFalse(c.getSourceName().contains(".env"));
            assertFalse(c.getSourceName().contains("secret"));
        }
    }
}
