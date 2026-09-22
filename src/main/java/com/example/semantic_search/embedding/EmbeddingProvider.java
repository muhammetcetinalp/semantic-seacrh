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

    /**
     * Normalizes a float vector to unit length (L2 norm = 1.0).
     * Ensures optimal numerical stability and accurate cosine similarity in FAISS / OpenSearch.
     */
    static float[] normalizeL2(float[] vector) {
        if (vector == null || vector.length == 0) {
            return vector;
        }
        double sumSq = 0.0;
        for (float v : vector) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 1e-9) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }
}
