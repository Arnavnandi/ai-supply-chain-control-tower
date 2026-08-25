package com.supplychain.controltower.controller;

import com.supplychain.controltower.entity.DocumentMetadata;
import com.supplychain.controltower.repository.DocumentMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DocumentControllerTest {

    private ChatClient chatClient;
    private VectorStore vectorStore;
    private DocumentMetadataRepository documentMetadataRepository;
    private DocumentController documentController;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        vectorStore = mock(VectorStore.class);
        documentMetadataRepository = mock(DocumentMetadataRepository.class);

        documentController = new DocumentController(chatClient, vectorStore, documentMetadataRepository);
    }

    @Test
    void testGetAllDocuments() {
        when(documentMetadataRepository.findAll()).thenReturn(List.of(
                DocumentMetadata.builder().id(1L).fileName("SOP_Procurement.pdf").chunkCount(5).build()
        ));

        ResponseEntity<List<DocumentMetadata>> response = documentController.getAllDocuments();
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("SOP_Procurement.pdf", response.getBody().get(0).getFileName());
        verify(documentMetadataRepository, times(1)).findAll();
    }

    @Test
    void testUploadDocumentSuccess() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-sop.txt",
                "text/plain",
                "Procurement policy guidelines for purchase orders above 5000 USD.".getBytes()
        );

        ResponseEntity<?> response = documentController.uploadDocument(file);
        assertEquals(200, response.getStatusCode().value());

        verify(vectorStore, times(1)).add(anyList());
        verify(documentMetadataRepository, times(1)).save(any(DocumentMetadata.class));
    }

    @Test
    void testQueryDocumentRagSuccess() {
        Document doc = new Document("All purchase orders over $5,000 require approval.");
        when(vectorStore.similaritySearch(anyString())).thenReturn(List.of(doc));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Based on company policy, orders over $5,000 require manager approval.");

        Map<String, String> request = Map.of("query", "What is the approval threshold?");
        ResponseEntity<Map<String, Object>> response = documentController.queryDocumentRag(request);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().get("sourcesCount"));
        assertTrue(response.getBody().get("groundedAnswer").toString().contains("manager approval"));
        verify(vectorStore, times(1)).similaritySearch("What is the approval threshold?");
    }

    @Test
    void testQueryDocumentRagWhenNoDocsFound() {
        when(vectorStore.similaritySearch(anyString())).thenReturn(List.of());
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("No document records matched your query.");

        Map<String, String> request = Map.of("query", "What is return policy?");
        ResponseEntity<Map<String, Object>> response = documentController.queryDocumentRag(request);

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().get("sourcesCount"));
        assertEquals("No document records matched your query.", response.getBody().get("groundedAnswer"));
    }
}
