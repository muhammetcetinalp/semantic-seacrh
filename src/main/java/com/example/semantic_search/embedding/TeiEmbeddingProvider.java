package com.example.semantic_search.embedding;

import com.example.semantic_search.configuration.EmbeddingProperties;
import com.example.semantic_search.exception.EmbeddingUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Embedding provider backed by Hugging Face Text Embeddings Inference (TEI).
 *
 * <p>TEI exposes a {@code POST /embed} endpoint that accepts
 * {@code {"inputs": "text"}} or {@code {"inputs": ["text1", "text2"]}}
 * and returns a bare {@code float[][]} array — no wrapping "data" key.
 *
 * <p>Activate via: {@code search.embedding.provider=tei}
 */
@Component
@ConditionalOnProperty(name = "search.embedding.provider", havingValue = "tei")
public class TeiEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(TeiEmbeddingProvider.class);

    private final RestClient restClient;
    private final EmbeddingProperties properties;

    public TeiEmbeddingProvider(RestClient.Builder restClientBuilder, EmbeddingProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getEndpoint())
                .build();
        this.properties = properties;
        log.info("TEI embedding provider active — endpoint={}, model={}, dims={}",
                properties.getEndpoint(), properties.getModel(), properties.getDimensions());
    }

    @Override
    @SuppressWarnings("unchecked")
    public float[] generateEmbedding(String text) {
        try {
            // TEI /embed accepts {"inputs": "single string"} → returns [[float, ...]]
            Map<String, Object> body = Map.of("inputs", text);

            List<List<Number>> response = restClient.post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(List.class);

            if (response == null || response.isEmpty()) {
                throw new EmbeddingUnavailableException("Empty response from TEI service");
            }

            // Response is [[v1, v2, ...]] for a single input
            List<Number> vector = (List<Number>) response.getFirst();
            float[] embedding = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                embedding[i] = vector.get(i).floatValue();
            }
            return EmbeddingProvider.normalizeL2(embedding);

        } catch (EmbeddingUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate embedding via TEI: {}", e.getMessage());
            throw new EmbeddingUnavailableException("TEI embedding service unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public int getDimensions() {
        return properties.getDimensions();
    }

    @Override
    public boolean isAvailable() {
        try {
            restClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("TEI health check failed: {}", e.getMessage());
            return false;
        }
    }
}
