package com.example.semantic_search.qdrant;

import java.util.List;
import java.util.Map;

public record QdrantPoint(
        String entityId,
        List<List<Float>> multiVector,
        Map<String, Object> payload
) {
}
