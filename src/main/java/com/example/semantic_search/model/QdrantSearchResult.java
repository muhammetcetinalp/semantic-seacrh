package com.example.semantic_search.model;

import java.util.Map;

/**
 * Qdrant vektör arama motorundan dönen tekil arama sonucu kaydı (Record).
 *
 * <p>ColBERT MaxSim sorgusu sonucunda Qdrant tarafından puanlanan noktayı, ilişkili varlık
 * kimliğini ve eşleşme benzerlik puanını barındırır.</p>
 *
 * @param pointId Qdrant iç nokta kimliği (Point ID)
 * @param entityId Orijinal arama dokümanı kimliği (Entity ID)
 * @param score Qdrant MaxSim operatörü tarafından hesaplanan benzerlik skoru
 * @param payload Noktaya bağlı meta veri yükü
 */
public record QdrantSearchResult(
        String pointId,
        String entityId,
        double score,
        Map<String, Object> payload
) {
}
