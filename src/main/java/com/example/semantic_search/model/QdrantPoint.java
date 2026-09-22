package com.example.semantic_search.model;

import java.util.List;
import java.util.Map;

/**
 * Qdrant vektör veritabanında saklanan çoklu vektör (ColBERT Multi-Vector) veri noktası kaydı (Record).
 *
 * <p>ColBERT mimarisinde her bir doküman tek bir vektör yerine, metindeki her token için
 * 128 boyutlu ayrı bir vektör (vektörler matrisi: List&lt;List&lt;Float&gt;&gt;) olarak saklanır.</p>
 *
 * @param entityId Orijinal dokümanın benzersiz kimliği (ID)
 * @param multiVector Token seviyesinde 128 boyutlu çoklu vektör listesi
 * @param payload Qdrant üzerinde saklanan filtreleme ve üstveri haritası
 */
public record QdrantPoint(
        String entityId,
        List<List<Float>> multiVector,
        Map<String, Object> payload
) {
}
