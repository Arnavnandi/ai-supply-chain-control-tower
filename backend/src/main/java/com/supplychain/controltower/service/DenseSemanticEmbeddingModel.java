package com.supplychain.controltower.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Primary
public class DenseSemanticEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 768;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            List<Double> doubleList = embed(instructions.get(i));
            embeddings.add(new Embedding(doubleList, i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public List<Double> embed(String text) {
        float[] floatVec = generate768DimensionVector(text);
        List<Double> doubleList = new ArrayList<>(floatVec.length);
        for (float f : floatVec) {
            doubleList.add((double) f);
        }
        return doubleList;
    }

    @Override
    public List<Double> embed(Document document) {
        return embed(document.getContent());
    }

    public static float[] generate768DimensionVector(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) return vector;

        String normalized = text.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", " ");
        String[] tokens = normalized.split("\\s+");

        for (String token : tokens) {
            if (token.isEmpty()) continue;
            int hash1 = Math.abs(token.hashCode());
            int idx1 = hash1 % DIMENSIONS;
            vector[idx1] += 1.0f;

            if (token.length() >= 3) {
                for (int i = 0; i <= token.length() - 3; i++) {
                    String sub = token.substring(i, i + 3);
                    int hash2 = Math.abs(sub.hashCode());
                    int idx2 = hash2 % DIMENSIONS;
                    vector[idx2] += 0.5f;
                }
            }
        }

        double norm = 0.0;
        for (float val : vector) {
            norm += val * val;
        }
        norm = Math.sqrt(norm);

        if (norm > 0.0) {
            for (int i = 0; i < DIMENSIONS; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }
}
