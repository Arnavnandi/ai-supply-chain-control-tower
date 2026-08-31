package com.supplychain.controltower.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.pgvector.dimensions:384}")
    private int dimensions;

    @Bean
    @Primary
    @Lazy
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, @Lazy EmbeddingModel embeddingModel) {
        return new PgVectorStore(
                jdbcTemplate,
                embeddingModel,
                dimensions,
                PgVectorStore.PgDistanceType.COSINE_DISTANCE,
                false, // removeExistingVectorStoreTable
                PgVectorStore.PgIndexType.HNSW,
                true   // initializeSchema
        );
    }
}
