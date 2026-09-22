package com.example.semantic_search.embedding;

import com.example.semantic_search.configuration.EmbeddingProperties;
import com.example.semantic_search.exception.EmbeddingUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Embedding provider that delegates to an external HTTP service.
 * Uses a generic request/response format compatible with common
 * embedding APIs (e.g., OpenAI-compatible interface).
 */
@Component
@ConditionalOnProperty(name = "search.embedding.provider", havingValue = "rest")
public class RestEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(RestEmbeddingProvider.class);

    private final RestClient restClient;
    private final EmbeddingProperties properties;

    public RestEmbeddingProvider(RestClient.Builder restClientBuilder, EmbeddingProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getEndpoint())
                .build();
        this.properties = properties;
        log.info("REST embedding provider active — endpoint={}, model={}",
                properties.getEndpoint(), properties.getModel());
    }

    @Override
    @SuppressWarnings("unchecked")
    public float[] generateEmbedding(String text) {
        try {
            Map<String, Object> request = Map.of(
                    "input", text,
                    "model", properties.getModel()
            );

            Map<String, Object> response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new EmbeddingUnavailableException("Empty response from embedding service");
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) {
                throw new EmbeddingUnavailableException("No embedding data in response");
            }

            List<Number> embeddingList = (List<Number>) data.getFirst().get("embedding");
            float[] embedding = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                embedding[i] = embeddingList.get(i).floatValue();
            }
            return EmbeddingProvider.normalizeL2(embedding);

        } catch (EmbeddingUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate embedding: {}", e.getMessage());
            throw new EmbeddingUnavailableException("Embedding service unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public int getDimensions() {
        return properties.getDimensions();
    }

    @Override
    public boolean isAvailable() {
        try {
            restClient.get().retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
