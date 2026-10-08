package com.supplychain.controltower.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "embedding.provider", havingValue = "gemini")
public class GeminiEmbeddingConfig {

    private static final Logger logger = LoggerFactory.getLogger(GeminiEmbeddingConfig.class);

    @Value("${spring.ai.openai.api-key:${GEMINI_API_KEY:}}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://generativelanguage.googleapis.com/v1beta/openai/}")
    private String baseUrl;

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        logger.info("[GEMINI EMBEDDING CONFIG] Initializing Gemini OpenAiEmbeddingModel with model=gemini-embedding-001, dimensions=768, baseUrl={}", baseUrl);

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestInterceptor(new GeminiUsageResponseInterceptor());

        OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey, restClientBuilder, WebClient.builder());

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .withModel("gemini-embedding-001")
                .withDimensions(768)
                .build();

        OpenAiEmbeddingModel delegate = new OpenAiEmbeddingModel(openAiApi, org.springframework.ai.document.MetadataMode.ALL, options);
        return new L2NormalizedEmbeddingModel(delegate);
    }

    private static class GeminiUsageResponseInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            ClientHttpResponse response = execution.execute(request, body);
            if (request.getURI().getPath().contains("/embeddings")) {
                byte[] responseBytes = StreamUtils.copyToByteArray(response.getBody());
                String json = new String(responseBytes, StandardCharsets.UTF_8);
                if (!json.contains("\"usage\"")) {
                    int lastBrace = json.lastIndexOf('}');
                    if (lastBrace != -1) {
                        String modifiedJson = json.substring(0, lastBrace) + ",\"usage\":{\"prompt_tokens\":0,\"total_tokens\":0}}";
                        return new ModifiedClientHttpResponse(response, modifiedJson.getBytes(StandardCharsets.UTF_8));
                    }
                }
                return new ModifiedClientHttpResponse(response, responseBytes);
            }
            return response;
        }
    }

    private static class ModifiedClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse originalResponse;
        private final byte[] body;

        public ModifiedClientHttpResponse(ClientHttpResponse originalResponse, byte[] body) {
            this.originalResponse = originalResponse;
            this.body = body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return originalResponse.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return originalResponse.getStatusCode();
        }

        @Override
        public int getRawStatusCode() throws IOException {
            return originalResponse.getRawStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return originalResponse.getStatusText();
        }

        @Override
        public void close() {
            originalResponse.close();
        }
    }

    public static class L2NormalizedEmbeddingModel implements EmbeddingModel {
        private final EmbeddingModel delegate;

        public L2NormalizedEmbeddingModel(EmbeddingModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<Double> embed(Document document) {
            return normalize(delegate.embed(document));
        }

        @Override
        public List<Double> embed(String text) {
            return normalize(delegate.embed(text));
        }

        @Override
        public List<List<Double>> embed(List<String> texts) {
            List<List<Double>> raw = delegate.embed(texts);
            if (raw == null) return null;
            List<List<Double>> result = new ArrayList<>(raw.size());
            for (List<Double> vec : raw) {
                result.add(normalize(vec));
            }
            return result;
        }

        @Override
        public EmbeddingResponse embedForResponse(List<String> texts) {
            return call(new EmbeddingRequest(texts, OpenAiEmbeddingOptions.builder().build()));
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            EmbeddingResponse rawResponse = delegate.call(request);
            List<Embedding> normalizedEmbeddings = new ArrayList<>();
            for (Embedding embedding : rawResponse.getResults()) {
                List<Double> normalizedVector = normalize(embedding.getOutput());
                normalizedEmbeddings.add(new Embedding(normalizedVector, embedding.getIndex()));
            }
            return new EmbeddingResponse(normalizedEmbeddings, rawResponse.getMetadata());
        }

        @Override
        public int dimensions() {
            return 768;
        }

        public static List<Double> normalize(List<Double> vector) {
            if (vector == null || vector.isEmpty()) {
                return vector;
            }
            double sumSquare = 0.0;
            for (Double val : vector) {
                if (val != null) {
                    sumSquare += val * val;
                }
            }
            double norm = Math.sqrt(sumSquare);
            if (norm < 1e-9 || Math.abs(norm - 1.0) < 1e-6) {
                return vector;
            }
            List<Double> normalized = new ArrayList<>(vector.size());
            for (Double val : vector) {
                normalized.add(val / norm);
            }
            return normalized;
        }
    }
}
