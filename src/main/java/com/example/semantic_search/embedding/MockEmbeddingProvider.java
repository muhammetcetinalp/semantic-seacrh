package com.example.semantic_search.embedding;

import com.example.semantic_search.configuration.EmbeddingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Deterministic mock embedding provider for development and testing.
 * Produces normalized vectors derived from the text hash so the same
 * input always yields the same embedding.
 */
@Component
@ConditionalOnProperty(
        name = "search.embedding.provider",
        havingValue = "mock",
        matchIfMissing = true)
public class MockEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(MockEmbeddingProvider.class);

    private final int dimensions;

    public MockEmbeddingProvider(EmbeddingProperties properties) {
        this.dimensions = properties.getDimensions();
        log.info("Mock embedding provider active — dimensions={}", dimensions);
    }

    @Override
    public float[] generateEmbedding(String text) {
        Random rng = new Random(text.hashCode());
        float[] embedding = new float[dimensions];
        float norm = 0f;
        for (int i = 0; i < dimensions; i++) {
            embedding[i] = rng.nextFloat() * 2 - 1;
            norm += embedding[i] * embedding[i];
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < dimensions; i++) {
            embedding[i] /= norm;
        }
        return embedding;
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
