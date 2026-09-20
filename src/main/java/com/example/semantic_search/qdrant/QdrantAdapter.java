package com.example.semantic_search.qdrant;

import com.example.semantic_search.configuration.QdrantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class QdrantAdapter {

    private static final Logger log = LoggerFactory.getLogger(QdrantAdapter.class);

    private final RestClient restClient;
    private final QdrantProperties properties;

    public QdrantAdapter(RestClient.Builder restClientBuilder, QdrantProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getEndpoint()).build();
    }

    public boolean isAvailable() {
        if (!properties.isEnabled()) {
            return false;
        }
        try {
            var entity = restClient.get().uri("/healthz").retrieve().toBodilessEntity();
            return entity.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public boolean ensureCollectionExists() {
        if (!isAvailable()) {
            return false;
        }
        String collection = properties.getCollectionName();
        try {
            // 1. Check if collection exists
            Map<String, Object> resp = restClient.get()
                    .uri("/collections/{collection}", collection)
                    .retrieve()
                    .body(Map.class);

            if (resp != null && "ok".equals(resp.get("status"))) {
                log.debug("Qdrant collection '{}' already exists", collection);
                return true;
            }
        } catch (Exception notFound) {
            // Collection doesn't exist yet, proceed to create
        }

        try {
            log.info("Creating Qdrant Multi-Vector collection '{}' with max_sim comparator...", collection);
            Map<String, Object> colbertConfig = Map.of(
                    "size", properties.getVectorSize(),
                    "distance", "Cosine",
                    "multivector_config", Map.of("comparator", "max_sim")
            );

            Map<String, Object> requestBody = Map.of(
                    "vectors", Map.of("colbert", colbertConfig)
            );

            Map<String, Object> createResp = restClient.put()
                    .uri("/collections/{collection}", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            log.info("Qdrant collection created: {}", createResp);
            return true;
        } catch (Exception e) {
            log.error("Failed to create Qdrant collection '{}': {}", collection, e.getMessage());
            return false;
        }
    }

    public boolean upsertPoint(String entityId, List<List<Float>> multiVector, Map<String, Object> payload) {
        return upsertPoints(List.of(new QdrantPoint(entityId, multiVector, payload)));
    }

    public boolean upsertPoints(List<QdrantPoint> points) {
        if (!isAvailable() || points.isEmpty()) {
            return false;
        }
        ensureCollectionExists();

        List<Map<String, Object>> qdrantPoints = new ArrayList<>();
        for (QdrantPoint pt : points) {
            String uuid = UUID.nameUUIDFromBytes(pt.entityId().getBytes(StandardCharsets.UTF_8)).toString();

            Map<String, Object> payload = new HashMap<>(pt.payload() != null ? pt.payload() : Map.of());
            payload.put("entity_id", pt.entityId());

            qdrantPoints.add(Map.of(
                    "id", uuid,
                    "vector", Map.of("colbert", pt.multiVector()),
                    "payload", payload
            ));
        }

        try {
            Map<String, Object> body = Map.of("points", qdrantPoints);
            restClient.put()
                    .uri("/collections/{collection}/points?wait=true", properties.getCollectionName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Upserted {} multi-vector points into Qdrant collection '{}'",
                    points.size(), properties.getCollectionName());
            return true;
        } catch (Exception e) {
            log.error("Failed to upsert points to Qdrant: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean deletePoint(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return false;
        }
        return deletePoints(List.of(entityId));
    }

    public boolean deletePoints(List<String> entityIds) {
        if (!isAvailable() || entityIds == null || entityIds.isEmpty()) {
            return false;
        }
        List<String> uuids = entityIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(id -> UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString())
                .toList();

        if (uuids.isEmpty()) {
            return false;
        }

        try {
            Map<String, Object> body = Map.of("points", uuids);
            restClient.post()
                    .uri("/collections/{collection}/points/delete?wait=true", properties.getCollectionName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Deleted {} multi-vector points from Qdrant collection '{}'",
                    uuids.size(), properties.getCollectionName());
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete points from Qdrant: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<QdrantSearchResult> searchMaxSim(List<List<Float>> queryVectors, int limit) {
        if (!isAvailable() || queryVectors == null || queryVectors.isEmpty()) {
            return List.of();
        }

        String collection = properties.getCollectionName();
        try {
            // First try Qdrant 1.10+ query API
            Map<String, Object> queryBody = Map.of(
                    "query", queryVectors,
                    "using", "colbert",
                    "limit", limit,
                    "with_payload", true
            );

            Map<String, Object> response = null;
            try {
                response = restClient.post()
                        .uri("/collections/{collection}/points/query", collection)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(queryBody)
                        .retrieve()
                        .body(Map.class);
            } catch (Exception queryApiErr) {
                // Fallback to /points/search API
                Map<String, Object> searchBody = Map.of(
                        "vector", Map.of("name", "colbert", "vector", queryVectors),
                        "limit", limit,
                        "with_payload", true
                );
                response = restClient.post()
                        .uri("/collections/{collection}/points/search", collection)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(searchBody)
                        .retrieve()
                        .body(Map.class);
            }

            if (response == null || !response.containsKey("result")) {
                return List.of();
            }

            List<Map<String, Object>> hits;
            Object resultObj = response.get("result");
            if (resultObj instanceof Map<?, ?> resMap && resMap.containsKey("points")) {
                hits = (List<Map<String, Object>>) resMap.get("points");
            } else if (resultObj instanceof List<?>) {
                hits = (List<Map<String, Object>>) resultObj;
            } else {
                return List.of();
            }

            List<QdrantSearchResult> results = new ArrayList<>();
            for (Map<String, Object> hit : hits) {
                String pointId = String.valueOf(hit.get("id"));
                double score = ((Number) hit.get("score")).doubleValue();
                Map<String, Object> payload = (Map<String, Object>) hit.getOrDefault("payload", Map.of());
                String entityId = (String) payload.getOrDefault("entity_id", pointId);

                results.add(new QdrantSearchResult(pointId, entityId, score, payload));
            }

            return results;
        } catch (Exception e) {
            log.error("Qdrant MaxSim search error: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
