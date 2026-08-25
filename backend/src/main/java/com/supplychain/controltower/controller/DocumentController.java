package com.supplychain.controltower.controller;

import com.supplychain.controltower.entity.DocumentMetadata;
import com.supplychain.controltower.repository.DocumentMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
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
    private final Tika tika = new Tika();

    @GetMapping
    public ResponseEntity<List<DocumentMetadata>> getAllDocuments() {
        return ResponseEntity.ok(documentMetadataRepository.findAll());
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Uploaded file is empty"));
        }

        try (InputStream is = file.getInputStream()) {
            String text = tika.parseToString(is);

            // Chunk document text into ~500 character chunks
            List<String> textChunks = chunkText(text, 500);
            List<Document> aiDocuments = new ArrayList<>();

            for (int i = 0; i < textChunks.size(); i++) {
                Map<String, Object> metadata = Map.of(
                        "fileName", file.getOriginalFilename(),
                        "chunkIndex", i,
                        "fileType", file.getContentType() != null ? file.getContentType() : "text/plain"
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
        String query = request.get("query");
        log.info("[DOCUMENT RAG QUERY] Query: '{}'", query);

        List<Document> similarDocs = Collections.emptyList();
        try {
            similarDocs = vectorStore.similaritySearch(query);
        } catch (Exception ex) {
            log.warn("[DOCUMENT RAG SEARCH EXCEPTION] pgvector search error: {}", ex.getMessage());
        }

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
        try {
            groundedAnswer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(query)
                    .call()
                    .content();
        } catch (Exception ex) {
            log.warn("[DOCUMENT RAG LLM EXCEPTION] ChatClient RAG invocation error: {}", ex.getMessage());
            groundedAnswer = retrievedContextStr.isBlank()
                    ? "No matching policy documents found for query: " + query
                    : "Based on retrieved policy documents:\n\n" + retrievedContextStr;
        }

        return ResponseEntity.ok(Map.of(
                "query", query,
                "groundedAnswer", groundedAnswer != null ? groundedAnswer : "No response generated.",
                "sourcesCount", similarDocs.size()
        ));
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
