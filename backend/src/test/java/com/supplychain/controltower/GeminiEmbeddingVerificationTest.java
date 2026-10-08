package com.supplychain.controltower;

import com.supplychain.controltower.config.GeminiEmbeddingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GeminiEmbeddingVerificationTest {

    @Test
    public void testL2Normalization() {
        List<Double> unnormalized = List.of(3.0, 4.0); // Norm is 5.0
        List<Double> normalized = GeminiEmbeddingConfig.L2NormalizedEmbeddingModel.normalize(unnormalized);

        assertEquals(0.6, normalized.get(0), 1e-5);
        assertEquals(0.8, normalized.get(1), 1e-5);

        double sumSq = normalized.get(0) * normalized.get(0) + normalized.get(1) * normalized.get(1);
        assertEquals(1.0, Math.sqrt(sumSq), 1e-5);
    }
}
