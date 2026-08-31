package com.supplychain.controltower.config;

import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class AiConfig {

    @Bean
    @Primary
    @Lazy
    public TransformersEmbeddingModel embeddingModel() {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.setModelResource(new ClassPathResource("onnx/all-MiniLM-L6-v2/model.onnx"));
        model.setTokenizerResource(new ClassPathResource("onnx/all-MiniLM-L6-v2/tokenizer.json"));
        model.setResourceCacheDirectory("/tmp/spring-ai-onnx-generative");
        return model;
    }
}
