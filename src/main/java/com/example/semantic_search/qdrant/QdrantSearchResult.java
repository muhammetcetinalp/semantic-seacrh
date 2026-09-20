package com.example.semantic_search.qdrant;

import java.util.Map;

public record QdrantSearchResult(
        String pointId,
        String entityId,
        double score,
        Map<String, Object> payload
) {
}
