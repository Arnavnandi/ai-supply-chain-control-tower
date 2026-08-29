package com.supplychain.controltower.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Configuration
public class AiConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.ai.onnx.enabled", havingValue = "true", matchIfMissing = false)
    public TransformersEmbeddingModel onnxEmbeddingModel() {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.setModelResource(new ClassPathResource("onnx/all-MiniLM-L6-v2/model.onnx"));
        model.setTokenizerResource(new ClassPathResource("onnx/all-MiniLM-L6-v2/tokenizer.json"));
        model.setResourceCacheDirectory("/tmp/spring-ai-onnx-generative");
        return model;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel fallbackEmbeddingModel() {
        return new EmbeddingModel() {
            private final List<Double> dummyVector = Collections.nCopies(384, 0.01);

            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<Embedding> embeddings = new ArrayList<>();
                for (int i = 0; i < request.getInstructions().size(); i++) {
                    embeddings.add(new Embedding(dummyVector, i));
                }
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public List<Double> embed(Document document) {
                return dummyVector;
            }

            @Override
            public List<Double> embed(String text) {
                return dummyVector;
            }
        };
    }
}
