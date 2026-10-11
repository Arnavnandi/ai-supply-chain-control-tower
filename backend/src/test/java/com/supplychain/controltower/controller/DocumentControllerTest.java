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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DocumentControllerTest {

    private ChatClient chatClient;
    private VectorStore vectorStore;
    private DocumentMetadataRepository documentMetadataRepository;
    private com.supplychain.controltower.repository.CompanyRepository companyRepository;
    private DocumentController documentController;

    @BeforeEach
    void setUp() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        vectorStore = mock(VectorStore.class);
        documentMetadataRepository = mock(DocumentMetadataRepository.class);
        companyRepository = mock(com.supplychain.controltower.repository.CompanyRepository.class);
        when(companyRepository.findById(anyLong())).thenReturn(Optional.of(com.supplychain.controltower.entity.Company.builder().id(1L).code("DEMO").name("Demo Corp").build()));

        documentController = new DocumentController(chatClient, vectorStore, documentMetadataRepository, companyRepository);
    }

    @Test
    void testGetAllDocuments() {
        when(documentMetadataRepository.findByCompanyId(1L)).thenReturn(List.of(
                DocumentMetadata.builder().id(1L).fileName("SOP_Procurement.pdf").chunkCount(5).build()
        ));

        ResponseEntity<List<DocumentMetadata>> response = documentController.getAllDocuments();
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("SOP_Procurement.pdf", response.getBody().get(0).getFileName());
        verify(documentMetadataRepository, times(1)).findByCompanyId(1L);
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
        documentController.setGeminiApiKey("AIzaSyTestValidKey123");
        Document doc = new Document("All purchase orders over $5,000 require approval.", Map.of("companyId", 1L, "documentScope", "COMPANY"));
        when(vectorStore.similaritySearch(anyString())).thenReturn(List.of(doc));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Based on company policy, orders over $5,000 require manager approval.");

        Map<String, String> request = Map.of("query", "What is the approval threshold?");
        ResponseEntity<Map<String, Object>> response = documentController.queryDocumentRag(request);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().get("sourcesCount"));
        assertEquals("GEMINI_LIVE", response.getBody().get("responseSource"));
        assertEquals(true, response.getBody().get("liveGemini"));
        assertNull(response.getBody().get("fallbackReason"));
        assertTrue(response.getBody().get("groundedAnswer").toString().contains("manager approval"));
        verify(vectorStore, times(1)).similaritySearch("What is the approval threshold?");
        verify(chatClient, atLeastOnce()).prompt();
    }

    @Test
    void testQueryDocumentRag_WhenApiKeyUnconfigured_SkipsLlmCallAndReturnsGroundedDocumentation() {
        documentController.setGeminiApiKey("unconfigured");
        Document doc = new Document("Air freight purchase orders are capped at $25,000.", Map.of("companyId", 1L, "documentScope", "COMPANY"));
        when(vectorStore.similaritySearch(anyString())).thenReturn(List.of(doc));

        Map<String, String> request = Map.of("query", "What is the air freight limit?");
        ResponseEntity<Map<String, Object>> response = documentController.queryDocumentRag(request);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().get("sourcesCount"));
        assertEquals("FALLBACK_DOCUMENTATION", response.getBody().get("responseSource"));
        assertEquals(false, response.getBody().get("liveGemini"));
        assertEquals("API_KEY_UNCONFIGURED", response.getBody().get("fallbackReason"));

        String answer = response.getBody().get("groundedAnswer").toString();
        assertTrue(answer.contains("[DOCUMENTATION FALLBACK - Gemini AI Unconfigured]"));
        assertTrue(answer.contains("not generated by live Gemini AI"));
        assertTrue(answer.contains("$25,000"));

        // Verify that chatClient was NEVER called - zero network latency
        verify(chatClient, never()).prompt();
    }

    @Test
    void testQueryDocumentRag_WhenApiKeyUnconfiguredAndNoDocsFound() {
        documentController.setGeminiApiKey("unconfigured");
        when(vectorStore.similaritySearch(anyString())).thenReturn(List.of());

        Map<String, String> request = Map.of("query", "What is return policy?");
        ResponseEntity<Map<String, Object>> response = documentController.queryDocumentRag(request);

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().get("sourcesCount"));
        assertEquals("FALLBACK_NO_CONTEXT", response.getBody().get("responseSource"));
        assertEquals(false, response.getBody().get("liveGemini"));
        assertEquals("API_KEY_UNCONFIGURED", response.getBody().get("fallbackReason"));
        assertTrue(response.getBody().get("groundedAnswer").toString().contains("[DOCUMENTATION SEARCH - No Live AI]"));

        verify(chatClient, never()).prompt();
    }

    @Test
    void testQueryDocumentRag_WhenGeminiInvocationFails_ReturnsClearFallback() {
        documentController.setGeminiApiKey("AIzaSyTestKeyWithRemoteError");
        Document doc = new Document("Standard supplier payment terms are Net 60.", Map.of("companyId", 1L, "documentScope", "COMPANY"));
        when(vectorStore.similaritySearch(anyString())).thenReturn(List.of(doc));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("400 Bad Request: Invalid or expired API Key"));

        Map<String, String> request = Map.of("query", "What are payment terms?");
        ResponseEntity<Map<String, Object>> response = documentController.queryDocumentRag(request);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().get("sourcesCount"));
        assertEquals("FALLBACK_DOCUMENTATION", response.getBody().get("responseSource"));
        assertEquals(false, response.getBody().get("liveGemini"));
        assertEquals("LLM_INVOCATION_FAILURE", response.getBody().get("fallbackReason"));

        String answer = response.getBody().get("groundedAnswer").toString();
        assertTrue(answer.contains("[DOCUMENTATION FALLBACK - Gemini AI Service Unavailable]"));
        assertTrue(answer.contains("not generated by live Gemini AI"));
        assertTrue(answer.contains("Net 60"));
    }
}
