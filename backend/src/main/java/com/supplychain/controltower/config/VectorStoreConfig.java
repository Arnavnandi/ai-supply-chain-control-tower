package com.supplychain.controltower.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    @Bean
    @Primary
    @Lazy
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, @Lazy EmbeddingModel embeddingModel) {
        return new PgVectorStore(
                jdbcTemplate,
                embeddingModel,
                768,
                PgVectorStore.PgDistanceType.COSINE_DISTANCE,
                false, // removeExistingVectorStoreTable
                PgVectorStore.PgIndexType.HNSW,
                true   // initializeSchema
        );
    }
}
