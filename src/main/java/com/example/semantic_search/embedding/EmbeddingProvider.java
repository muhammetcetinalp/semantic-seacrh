package com.example.semantic_search.embedding;

/**
 * Abstraction for vector embedding generation.
 *
 * <p>The embedding model is decoupled from OpenSearch so it can be
 * swapped independently — local model, remote API, GPU server, etc.</p>
 */
public interface EmbeddingProvider {

    float[] generateEmbedding(String text);

    int getDimensions();

    boolean isAvailable();
}
