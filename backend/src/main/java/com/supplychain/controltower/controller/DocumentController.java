package com.supplychain.controltower.controller;

import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.DocumentMetadata;
import com.supplychain.controltower.exception.TenantContextMissingException;
import com.supplychain.controltower.repository.CompanyRepository;
import com.supplychain.controltower.repository.DocumentMetadataRepository;
import com.supplychain.controltower.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final CompanyRepository companyRepository;
    private final Tika tika = new Tika();

    @Value("${spring.ai.openai.api-key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public boolean isGeminiApiKeyConfigured() {
        return geminiApiKey != null
                && !geminiApiKey.isBlank()
                && !"unconfigured".equalsIgnoreCase(geminiApiKey.trim())
                && !geminiApiKey.contains("your-api-key");
    }

    @GetMapping
    public ResponseEntity<List<DocumentMetadata>> getAllDocuments() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return ResponseEntity.ok(documentMetadataRepository.findByCompanyId(companyId));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Uploaded file is empty"));
        }

        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found: " + companyId));

        try {
            String text = null;
            try (InputStream is = file.getInputStream()) {
                text = tika.parseToString(is);
            } catch (Exception e) {
                log.debug("Tika parsing fallback to raw text: {}", e.getMessage());
            }
            if (text == null || text.isBlank()) {
                text = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            // Chunk document text into ~500 character chunks
            List<String> textChunks = chunkText(text, 500);
            List<Document> aiDocuments = new ArrayList<>();

            for (int i = 0; i < textChunks.size(); i++) {
                Map<String, Object> metadata = Map.of(
                        "fileName", file.getOriginalFilename(),
                        "chunkIndex", i,
                        "fileType", file.getContentType() != null ? file.getContentType() : "text/plain",
                        "companyId", companyId,
                        "documentScope", "COMPANY"
                );
                aiDocuments.add(new Document(textChunks.get(i), metadata));
            }

            // Save to pgvector VectorStore if embeddings are configured
            try {
                vectorStore.add(aiDocuments);
            } catch (Exception ex) {
                log.warn("VectorStore embedding fallback: {}", ex.getMessage());
            }

            DocumentMetadata meta = DocumentMetadata.builder()
                    .company(company)
                    .fileName(file.getOriginalFilename())
                    .fileType(file.getContentType())
                    .fileSize(file.getSize())
                    .uploadTime(LocalDateTime.now())
                    .summary("Extracted " + textChunks.size() + " text chunks for policy RAG query.")
                    .chunkCount(textChunks.size())
                    .build();

            documentMetadataRepository.save(meta);

            return ResponseEntity.ok(Map.of(
                    "message", "Document uploaded and indexed successfully!",
                    "fileName", file.getOriginalFilename(),
                    "chunksIndexed", textChunks.size()
            ));

        } catch (Exception e) {
            log.error("Failed to process document upload", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Document processing error: " + e.getMessage()));
        }
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> queryDocumentRag(@RequestBody Map<String, String> request) {
        Long companyId = TenantContext.getRequiredCompanyId();
        String query = request.get("query");
        log.info("[DOCUMENT RAG QUERY] CompanyId: {}, Query: '{}'", companyId, query);

        List<Document> rawDocs = Collections.emptyList();
        try {
            rawDocs = vectorStore.similaritySearch(query);
        } catch (Exception ex) {
            log.warn("[DOCUMENT RAG SEARCH EXCEPTION] pgvector search error: {}", ex.getMessage());
        }

        // Filter documents: include if documentScope == 'SYSTEM' OR companyId matches
        List<Document> similarDocs = rawDocs.stream().filter(doc -> {
            Map<String, Object> meta = doc.getMetadata();
            if (meta == null) return true;
            String scope = String.valueOf(meta.getOrDefault("documentScope", ""));
            Object docCompanyId = meta.get("companyId");
            if ("SYSTEM".equalsIgnoreCase(scope) || "0".equals(String.valueOf(docCompanyId))) {
                return true;
            }
            return docCompanyId != null && String.valueOf(companyId).equals(String.valueOf(docCompanyId));
        }).toList();

        StringBuilder groundedContext = new StringBuilder();
        for (Document doc : similarDocs) {
            groundedContext.append(" - ").append(doc.getContent()).append("\n");
        }

        String retrievedContextStr = groundedContext.toString().trim();
        String systemPrompt = """
                You are an Enterprise Supply Chain Policy & SOP Specialist Assistant.
                Answer the user's question based strictly on the provided company policy documentation context below.
                If no matching policy text is provided, state clearly that no document records matched the query and provide general guidance.

                RETRIEVED POLICY DOCUMENTATION CONTEXT:
                %s
                """.formatted(retrievedContextStr.isBlank() ? "No matching document chunks found in vector store." : retrievedContextStr);

        String groundedAnswer;
        String responseSource;
        String fallbackReason = null;

        if (!isGeminiApiKeyConfigured()) {
            log.info("[DOCUMENT RAG] Gemini API key unconfigured/absent. Returning grounded policy documentation without external call.");
            responseSource = retrievedContextStr.isBlank() ? "FALLBACK_NO_CONTEXT" : "FALLBACK_DOCUMENTATION";
            fallbackReason = "API_KEY_UNCONFIGURED";
            groundedAnswer = buildFallbackAnswer(query, retrievedContextStr, fallbackReason);
        } else {
            try {
                groundedAnswer = chatClient.prompt()
                        .system(systemPrompt)
                        .user(query)
                        .call()
                        .content();
                responseSource = "GEMINI_LIVE";
            } catch (Exception ex) {
                log.warn("[DOCUMENT RAG LLM EXCEPTION] ChatClient RAG invocation error: {}", ex.getMessage());
                responseSource = retrievedContextStr.isBlank() ? "FALLBACK_NO_CONTEXT" : "FALLBACK_DOCUMENTATION";
                fallbackReason = "LLM_INVOCATION_FAILURE";
                groundedAnswer = buildFallbackAnswer(query, retrievedContextStr, fallbackReason);
            }
        }

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("query", query);
        responseBody.put("groundedAnswer", groundedAnswer != null ? groundedAnswer : "No response generated.");
        responseBody.put("sourcesCount", similarDocs.size());
        responseBody.put("responseSource", responseSource);
        responseBody.put("liveGemini", "GEMINI_LIVE".equals(responseSource));
        if (fallbackReason != null) {
            responseBody.put("fallbackReason", fallbackReason);
        }

        return ResponseEntity.ok(responseBody);
    }

    private String buildFallbackAnswer(String query, String retrievedContext, String fallbackReason) {
        if (retrievedContext == null || retrievedContext.isBlank()) {
            return "[DOCUMENTATION SEARCH - No Live AI] No matching company policy documents found for query: " + query;
        }

        String notice = "API_KEY_UNCONFIGURED".equals(fallbackReason)
                ? "[DOCUMENTATION FALLBACK - Gemini AI Unconfigured] "
                : "[DOCUMENTATION FALLBACK - Gemini AI Service Unavailable] ";

        return notice + "The following answer is derived directly from retrieved company policy documents (not generated by live Gemini AI):\n\n"
                + retrievedContext;
    }

    private List<String> chunkText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        int length = text.length();
        for (int i = 0; i < length; i += chunkSize) {
            chunks.add(text.substring(i, Math.min(length, i + chunkSize)));
        }
        return chunks;
    }
}

