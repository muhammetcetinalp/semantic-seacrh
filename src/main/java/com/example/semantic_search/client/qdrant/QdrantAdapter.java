package com.example.semantic_search.client.qdrant;

import com.example.semantic_search.config.QdrantProperties;
import com.example.semantic_search.model.QdrantPoint;
import com.example.semantic_search.model.QdrantSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Qdrant vektör veritabanı ile HTTP REST API üzerinden iletişim kuran istemci adaptörü.
 *
 * <p>Özellikle ColBERT çoklu-vektör (multi-vector / token-level late interaction) mimarisini
 * desteklemek üzere tasarlanmıştır. Koleksiyon yönetimi, noktasal veya toplu vektör ekleme (upsert),
 * silme ve yerel MaxSim (kosinüs tabanlı maksimum benzerlik) arama işlemlerini yönetir.</p>
 */
@Component
public class QdrantAdapter {

    private static final Logger log = LoggerFactory.getLogger(QdrantAdapter.class);

    private final RestClient restClient;
    private final QdrantProperties properties;

    /**
     * QdrantAdapter bileşenini yapılandıran yapıcı metot.
     *
     * @param restClientBuilder Spring RestClient yapıcısı
     * @param properties Qdrant bağlantı ve koleksiyon özellikleri
     */
    public QdrantAdapter(RestClient.Builder restClientBuilder, QdrantProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getEndpoint()).build();
    }

    /**
     * Qdrant servisinin aktif ve erişilebilir olup olmadığını test eder.
     *
     * @return Qdrant ayakta ve HTTP 2xx dönüyorsa true, aksi halde false
     */
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

    /**
     * Hedef Qdrant koleksiyonunun mevcut olup olmadığını kontrol eder;
     * mevcut değilse ColBERT çoklu vektör ve max_sim karşılaştırıcısı ile koleksiyonu oluşturur.
     *
     * @return Koleksiyon kullanıma hazırsa true, oluşturulamadıysa false
     */
    @SuppressWarnings("unchecked")
    public boolean ensureCollectionExists() {
        if (!isAvailable()) {
            return false;
        }
        String collection = properties.getCollectionName();
        try {
            Map<String, Object> resp = restClient.get()
                    .uri("/collections/{collection}", collection)
                    .retrieve()
                    .body(Map.class);

            if (resp != null && "ok".equals(resp.get("status"))) {
                log.debug("Qdrant koleksiyonu '{}' zaten mevcut", collection);
                return true;
            }
        } catch (Exception notFound) {
            // Koleksiyon henüz mevcut değil, oluşturulmaya devam edilecek
        }

        try {
            log.info("Qdrant çoklu vektör (Multi-Vector) koleksiyonu '{}' max_sim ile oluşturuluyor...", collection);
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

            log.info("Qdrant koleksiyonu oluşturuldu: {}", createResp);
            return true;
        } catch (Exception e) {
            log.error("Qdrant koleksiyonu '{}' oluşturulamadı: {}", collection, e.getMessage());
            return false;
        }
    }

    /**
     * Tek bir varlığa ait ColBERT çoklu vektörünü ve meta verisini Qdrant'a ekler veya günceller.
     *
     * @param entityId Doküman / varlık ID'si
     * @param multiVector Token düzeyinde gömme vektörleri listesi
     * @param payload İlişkili meta veri yükü
     * @return İşlem başarılı ise true
     */
    public boolean upsertPoint(String entityId, List<List<Float>> multiVector, Map<String, Object> payload) {
        return upsertPoints(List.of(new QdrantPoint(entityId, multiVector, payload)));
    }

    /**
     * Çoklu varlık noktalarını ({@link QdrantPoint}) Qdrant koleksiyonuna toplu olarak ekler (upsert).
     *
     * @param points Eklenecek noktalar listesi
     * @return Başarılı ise true
     */
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

            log.info("Qdrant koleksiyonuna '{}' {} adet çoklu vektör noktası eklendi",
                    properties.getCollectionName(), points.size());
            return true;
        } catch (Exception e) {
            log.error("Qdrant noktaları eklenemedi: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Belirtilen varlık ID'sine sahip noktayı Qdrant'tan siler.
     *
     * @param entityId Varlık ID'si
     * @return Başarılı ise true
     */
    public boolean deletePoint(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return false;
        }
        return deletePoints(List.of(entityId));
    }

    /**
     * Verilen varlık ID listesindeki noktaları Qdrant'tan toplu olarak siler.
     *
     * @param entityIds Varlık ID listesi
     * @return Başarılı ise true
     */
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

            log.info("Qdrant koleksiyonundan '{}' {} adet nokta silindi",
                    properties.getCollectionName(), uuids.size());
            return true;
        } catch (Exception e) {
            log.warn("Qdrant noktaları silinemedi: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Filtre olmadan sorgu vektörleri ile MaxSim benzerlik araması çalıştırır.
     *
     * @param queryVectors Sorgu token vektörleri listesi
     * @param limit Sonuç adedi
     * @return Qdrant arama sonuçları listesi
     */
    @SuppressWarnings("unchecked")
    public List<QdrantSearchResult> searchMaxSim(List<List<Float>> queryVectors, int limit) {
        return searchMaxSim(queryVectors, Map.of(), limit);
    }

    /**
     * Qdrant çoklu vektör MaxSim sorgusunu filtreler ile çalıştırır.
     *
     * <p>Öncelikle Qdrant 1.10+ /points/query API'sini dener, geriye dönük uyumluluk için
     * /points/search API'sine geri düşer (fallback).</p>
     *
     * @param queryVectors Sorgu token vektörleri listesi
     * @param filters Filtre kriterleri
     * @param limit Sonuç limiti
     * @return Qdrant arama sonuçları listesi
     */
    @SuppressWarnings("unchecked")
    public List<QdrantSearchResult> searchMaxSim(List<List<Float>> queryVectors, Map<String, Object> filters, int limit) {
        if (!isAvailable() || queryVectors == null || queryVectors.isEmpty()) {
            return List.of();
        }

        String collection = properties.getCollectionName();
        try {
            Map<String, Object> qdrantFilter = buildQdrantFilter(filters);

            // Önce Qdrant 1.10+ query API'si denenir
            Map<String, Object> queryBody = new HashMap<>();
            queryBody.put("query", queryVectors);
            queryBody.put("using", "colbert");
            queryBody.put("limit", limit);
            queryBody.put("with_payload", true);
            if (qdrantFilter != null) {
                queryBody.put("filter", qdrantFilter);
            }

            Map<String, Object> response = null;
            try {
                response = restClient.post()
                        .uri("/collections/{collection}/points/query", collection)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(queryBody)
                        .retrieve()
                        .body(Map.class);
            } catch (Exception queryApiErr) {
                // /points/search API'sine fallback
                Map<String, Object> searchBody = new HashMap<>();
                searchBody.put("vector", Map.of("name", "colbert", "vector", queryVectors));
                searchBody.put("limit", limit);
                searchBody.put("with_payload", true);
                if (qdrantFilter != null) {
                    searchBody.put("filter", qdrantFilter);
                }

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
            log.error("Qdrant MaxSim arama hatası: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Verilen filtre haritasını Qdrant'ın beklediği filtre JSON yapısına dönüştürür.
     *
     * @param filters Filtre kriterleri
     * @return Qdrant filter haritası veya null
     */
    private Map<String, Object> buildQdrantFilter(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> mustList = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;

            if (value instanceof List<?> listVal) {
                List<String> strList = listVal.stream().map(Object::toString).toList();
                mustList.add(Map.of("key", key, "match", Map.of("any", strList)));
            } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                mustList.add(Map.of("key", key, "match", Map.of("value", value)));
            }
        }
        if (mustList.isEmpty()) {
            return null;
        }
        return Map.of("must", mustList);
    }
}
