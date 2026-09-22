package com.example.semantic_search.client.opensearch;

import com.example.semantic_search.config.EmbeddingProperties;
import com.example.semantic_search.dto.SearchResult;
import com.example.semantic_search.exception.DocumentNotFoundException;
import com.example.semantic_search.exception.OpenSearchUnavailableException;
import com.example.semantic_search.model.SearchDocument;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * OpenSearch kümesi ile olan tüm düşük seviyeli iletişimleri ve sorgu mantıklarını izole eden istemci adaptörü.
 *
 * <p>Bu adaptör şu sorumlulukları üstlenir:
 * <ul>
 *   <li>Türkçe dil çözümleme (Turkish analyzer: stemmer, stopword, korumalı lokasyon anahtar kelimeleri) ve HNSW vektör indeks yapılandırması.</li>
 *   <li>Doküman CRUD (tekil ve toplu indeksleme, ID ile sorgulama, silme) işlemleri.</li>
 *   <li>BM25 metin tabanlı arama sorguları ve alan ağırlıklandırmaları (ör. title^3.0, shortText^2.0).</li>
 *   <li>k-NN kosinüs benzerliği vektör aramaları.</li>
 *   <li>RRF (Reciprocal Rank Fusion) ve normalize puan birleştirme (Normalized Score Fusion) algoritmalarıyla hibrit arama.</li>
 *   <li>Coğrafi konum (geo_distance) ve tarih/aralık filtrelemeleri.</li>
 * </ul>
 * </p>
 */
@Component
public class OpenSearchAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchAdapter.class);
    private static final int RRF_RANK_CONSTANT = 60;
    private static final Set<String> CORE_FIELDS = Set.of(
            "id", "type", "title", "searchText",
            "metadata", "embedding", "createdAt", "updatedAt",
            "shortText", "longText", "birim", "adres", "tarih", "konum"
    );

    private final OpenSearchClient client;
    private final EmbeddingProperties embeddingProperties;
    private final OpenSearchDocumentSourceMapper documentSourceMapper;
    private final ObjectMapper objectMapper;

    /**
     * OpenSearchAdapter bileşenini yapılandıran yapıcı metot.
     *
     * @param client OpenSearch resmi Java istemcisi
     * @param embeddingProperties Vektör boyutları ve model yapılandırması
     * @param documentSourceMapper Doküman-kaynak dönüştürücü
     * @param objectMapper JSON ayrıştırma nesnesi
     */
    public OpenSearchAdapter(OpenSearchClient client, EmbeddingProperties embeddingProperties,
                             OpenSearchDocumentSourceMapper documentSourceMapper,
                             ObjectMapper objectMapper) {
        this.client = client;
        this.embeddingProperties = embeddingProperties;
        this.documentSourceMapper = documentSourceMapper;
        this.objectMapper = objectMapper;
    }

    // ---- İndeks Yönetimi ----

    /**
     * Belirtilen isimde bir indeks henüz mevcut değilse, Türkçe dilbilgisi kurallarına uygun
     * özel analyzer ve k-NN FAISS HNSW vektör alanı tanımlarıyla indeksi oluşturur.
     *
     * @param indexName Oluşturulacak indeksin adı
     * @throws OpenSearchUnavailableException İndeks oluşturma sırasında ağ veya OpenSearch hatası olursa
     */
    public void createIndexIfNotExists(String indexName) {
        try {
            boolean exists = client.indices().exists(e -> e.index(indexName)).value();
            if (!exists) {
                List<String> protectedKeywords = loadProtectedLocationKeywords();

                client.indices().create(c -> c
                        .index(indexName)
                        .settings(s -> s
                                .index(i -> i
                                        .knn(true)
                                        .numberOfShards(1)
                                        .numberOfReplicas(0)
                                )
                                .analysis(a -> a
                                        .analyzer("turkish_search", an -> an
                                                .custom(cu -> cu
                                                        .tokenizer("standard")
                                                        .filter("apostrophe", "lowercase",
                                                                "turkish_keywords",
                                                                "turkish_stop", "turkish_stemmer")))
                                        .filter("turkish_keywords", f -> f
                                                .definition(fd -> fd
                                                        .keywordMarker(km -> km
                                                                .keywords(protectedKeywords))))
                                        .filter("turkish_stop", f -> f
                                                .definition(fd -> fd
                                                        .stop(st -> st.stopwords("_turkish_"))))
                                        .filter("turkish_stemmer", f -> f
                                                .definition(fd -> fd
                                                        .stemmer(st -> st.language("turkish"))))
                                )
                        )
                        .mappings(m -> m
                                .properties("id", p -> p.keyword(k -> k))
                                .properties("type", p -> p.text(t -> t
                                        .analyzer("turkish_search")
                                        .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))))
                                .properties("title", p -> p.text(t -> t
                                        .analyzer("turkish_search")
                                        .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))))
                                .properties("searchText", p -> p.text(t -> t
                                        .analyzer("turkish_search")))
                                .properties("shortText", p -> p.text(t -> t
                                        .analyzer("turkish_search")))
                                .properties("longText", p -> p.text(t -> t
                                        .analyzer("turkish_search")))
                                .properties("birim", p -> p.text(t -> t
                                        .analyzer("turkish_search")
                                        .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))))
                                .properties("adres", p -> p.text(t -> t
                                        .analyzer("turkish_search")
                                        .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))))
                                .properties("tarih", p -> p.date(d -> d))
                                .properties("konum", p -> p.geoPoint(gp -> gp))
                                .properties("createdAt", p -> p.date(d -> d))
                                .properties("updatedAt", p -> p.date(d -> d))
                                .properties("embedding", p -> p.knnVector(knn -> knn
                                        .dimension(embeddingProperties.getDimensions())
                                        .method(method -> method
                                                .name("hnsw")
                                                .spaceType("cosinesimil")
                                                .engine("faiss")
                                                .parameters(Map.of(
                                                        "ef_construction", JsonData.of(256),
                                                        "m", JsonData.of(16)
                                                ))
                                        )
                                ))
                                .dynamic(DynamicMapping.True)
                        )
                );
                log.info("Created index '{}' with Turkish analyzer + HNSW (dim={}, ef=256, m=16)",
                        indexName, embeddingProperties.getDimensions());
            }
        } catch (IOException e) {
            throw new OpenSearchUnavailableException("İndeks oluşturulamadı: " + indexName, e);
        }
    }

    /**
     * Belirtilen indeksi OpenSearch üzerinden tamamen siler.
     *
     * @param indexName Silinecek indeks adı
     * @throws OpenSearchUnavailableException Silme işlemi başarısız olursa
     */
    public void deleteIndex(String indexName) {
        try {
            boolean exists = client.indices().exists(e -> e.index(indexName)).value();
            if (exists) {
                client.indices().delete(d -> d.index(indexName));
                log.info("Deleted index '{}'", indexName);
            }
        } catch (IOException e) {
            throw new OpenSearchUnavailableException("İndeks silinemedi: " + indexName, e);
        }
    }

    /**
     * Türkiye'deki il ve ilçe isimlerinin stemmer tarafından kökünün bozulmasını önlemek için
     * korumalı anahtar kelime listesini (protected location keywords) kaynak dosyasından yükler.
     *
     * @return Korumalı yer isimleri listesi
     */
    @SuppressWarnings("unchecked")
    private List<String> loadProtectedLocationKeywords() {
        Set<String> keywords = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        try (var is = getClass().getResourceAsStream("/data/turkey_locations.json")) {
            if (is != null) {
                Map<String, Object> map = objectMapper.readValue(is, Map.class);
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String cityKey = entry.getKey();
                    String cityName = cityKey.contains("-") ? cityKey.substring(cityKey.indexOf('-') + 1).trim() : cityKey.trim();
                    addLocationKeyword(keywords, cityName);
                    if (entry.getValue() instanceof List<?> list) {
                        for (Object distObj : list) {
                            if (distObj instanceof String dist) {
                                addLocationKeyword(keywords, dist);
                            }
                        }
                    }
                }
                log.info("Loaded {} protected location keywords from turkey_locations.json", keywords.size());
            } else {
                log.warn("turkey_locations.json classpath içinde bulunamadı");
            }
        } catch (Exception e) {
            log.warn("turkey_locations.json yüklenemedi: {}", e.getMessage());
        }

        return new ArrayList<>(keywords);
    }

    /**
     * Yer adını hem Türkçe hem de evrensel küçük harf formatında koruma listesine ekler.
     *
     * @param set Eklenecek küme
     * @param raw Ham yer adı
     */
    private void addLocationKeyword(Set<String> set, String raw) {
        if (raw == null || raw.isBlank()) return;
        String clean = raw.trim();
        set.add(clean.toLowerCase(Locale.forLanguageTag("tr")));
        set.add(clean.toLowerCase(Locale.ROOT));
    }

    // ---- Doküman CRUD İşlemleri ----

    /**
     * Tek bir dokümanı belirtilen hedef indekse yazar veya günceller.
     *
     * @param document İndekslenecek doküman modeli
     * @throws OpenSearchUnavailableException OpenSearch erişim hatası olursa
     */
    public void indexDocument(SearchDocument document) {
        try {
            Map<String, Object> docMap = documentSourceMapper.toSource(document);
            client.index(i -> i
                    .index(document.getIndexName())
                    .id(document.getId())
                    .document(docMap)
            );
            log.debug("Indexed document {} in {}", document.getId(), document.getIndexName());
        } catch (IOException e) {
            throw new OpenSearchUnavailableException("Doküman indekslenemedi: " + document.getId(), e);
        }
    }

    /**
     * Çoklu doküman listesini OpenSearch Bulk API kullanarak yüksek performansla toplu indeksler.
     *
     * @param documents İndekslenecek doküman listesi
     * @throws OpenSearchUnavailableException Toplu işlem sırasında iletişim hatası oluşursa
     */
    public void bulkIndex(List<SearchDocument> documents) {
        if (documents == null || documents.isEmpty()) return;

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (SearchDocument doc : documents) {
                Map<String, Object> docMap = documentSourceMapper.toSource(doc);
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(doc.getIndexName())
                                .id(doc.getId())
                                .document(docMap)
                        )
                );
            }

            BulkResponse response = client.bulk(bulkBuilder.build());
            if (response.errors()) {
                long errorCount = response.items().stream()
                        .filter(item -> item.error() != null)
                        .count();
                log.error("Bulk indexing completed with {} errors out of {} documents",
                        errorCount, documents.size());
            } else {
                log.debug("Bulk indexed {} documents", documents.size());
            }
        } catch (IOException e) {
            throw new OpenSearchUnavailableException("Toplu indeksleme işlemi başarısız", e);
        }
    }

    /**
     * Belirtilen dokümanı indeksten siler.
     *
     * @param indexName Hedef indeks
     * @param documentId Silinecek doküman ID'si
     * @throws OpenSearchUnavailableException Silme sırasında iletişim hatası olursa
     */
    public void deleteDocument(String indexName, String documentId) {
        try {
            DeleteResponse response = client.delete(d -> d
                    .index(indexName)
                    .id(documentId)
            );
            log.debug("Deleted document {} from {} (result={})", documentId, indexName, response.result());
        } catch (OpenSearchException e) {
            if (e.status() != 404) throw e;
            log.debug("Document {} in {} is already absent", documentId, indexName);
        } catch (IOException e) {
            throw new OpenSearchUnavailableException("Doküman silinemedi: " + documentId, e);
        }
    }

    /**
     * Dokümanın OpenSearch üzerindeki ham kaynak haritasını (source map) getirir.
     *
     * @param indexName Hedef indeks
     * @param documentId Doküman ID'si
     * @return Dokümana ait ham kaynak veri haritası
     * @throws DocumentNotFoundException Doküman bulunamazsa
     * @throws OpenSearchUnavailableException İletişim hatası oluşursa
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDocument(String indexName, String documentId) {
        try {
            GetResponse<Map> response = client.get(g -> g
                    .index(indexName)
                    .id(documentId), Map.class);

            if (!response.found()) {
                throw new DocumentNotFoundException(documentId, indexName);
            }
            return response.source();
        } catch (DocumentNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new OpenSearchUnavailableException("Doküman alınamadı: " + documentId, e);
        }
    }

    // ---- Arama Operasyonları ----

    /**
     * BM25 tam metin araması çalıştırır (offset = 0 varsayılanı ile).
     *
     * @param indexName Hedef indeks
     * @param queryText Kullanıcının arama metni
     * @param filters Filtreleme kriterleri
     * @param limit Dönecek maksimum sonuç sayısı
     * @return Eşleşen arama sonuçları listesi
     */
    @SuppressWarnings("unchecked")
    public List<SearchResult> bm25Search(String indexName, String queryText,
                                          Map<String, Object> filters, int limit) {
        return bm25Search(indexName, queryText, filters, limit, 0);
    }

    /**
     * BM25 tam metin aramasını sayfalama (offset) desteği ile çalıştırır.
     *
     * @param indexName Hedef indeks
     * @param queryText Kullanıcının arama metni
     * @param filters Filtreleme kriterleri
     * @param limit Sonuç adedi
     * @param offset Başlangıç ötelemesi
     * @return Eşleşen arama sonuçları listesi
     * @throws OpenSearchUnavailableException OpenSearch sorgusu başarısız olursa
     */
    @SuppressWarnings("unchecked")
    public List<SearchResult> bm25Search(String indexName, String queryText,
                                          Map<String, Object> filters, int limit, int offset) {
        try {
            Query query = buildBm25QueryWithFilters(queryText, filters);
            SearchResponse<Map> response = client.search(s -> s
                    .index(indexName)
                    .query(query)
                    .from(Math.max(0, offset))
                    .size(limit), Map.class);
            return mapResults(response);
        } catch (IOException e) {
            throw new OpenSearchUnavailableException("BM25 arama sorgusu başarısız", e);
        }
    }

    /**
     * k-NN kosinüs benzerliği vektör araması çalıştırır (offset = 0 ile).
     *
     * @param indexName Hedef indeks
     * @param queryVector Sorgu metninin yoğun embedding vektörü
     * @param filters Filtreleme kriterleri
     * @param limit Sonuç adedi
     * @return Vektörel olarak en yakın sonuçlar
     */
    @SuppressWarnings("unchecked")
    public List<SearchResult> vectorSearch(String indexName, float[] queryVector,
                                            Map<String, Object> filters, int limit) {
        return vectorSearch(indexName, queryVector, filters, limit, 0);
    }

    /**
     * k-NN kosinüs benzerliği vektör aramasını sayfalama desteği ile çalıştırır.
     *
     * @param indexName Hedef indeks
     * @param queryVector Sorgu yoğun embedding vektörü
     * @param filters Filtreleme kriterleri
     * @param limit Sonuç adedi
     * @param offset Başlangıç ötelemesi
     * @return Vektörel sonuçlar listesi
     * @throws OpenSearchUnavailableException Arama başarısız olursa
     */
    @SuppressWarnings("unchecked")
    public List<SearchResult> vectorSearch(String indexName, float[] queryVector,
                                            Map<String, Object> filters, int limit, int offset) {
        try {
            int k = limit + Math.max(0, offset);
            List<Float> vectorList = toFloatList(queryVector);
            Query query = buildVectorQueryWithFilters(vectorList, filters, k);

            SearchResponse<Map> response = client.search(s -> s
                    .index(indexName)
                    .query(query)
                    .from(Math.max(0, offset))
                    .size(limit), Map.class);
            return mapResults(response);
        } catch (IOException e) {
            throw new OpenSearchUnavailableException("Vektör arama sorgusu başarısız", e);
        }
    }

    /**
     * Filtrelere uyan aday dokümanları puanlama olmaksızın getirir.
     *
     * @param indexName Hedef indeks
     * @param filters Filtre haritası
     * @param limit Aday doküman sayısı
     * @return Aday arama sonuçları listesi
     */
    @SuppressWarnings("unchecked")
    public List<SearchResult> getCandidates(String indexName, Map<String, Object> filters, int limit) {
        try {
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
            boolBuilder.must(m -> m.matchAll(ma -> ma));
            addFilters(boolBuilder, filters);
            Query query = Query.of(q -> q.bool(boolBuilder.build()));
            SearchResponse<Map> response = client.search(s -> s
                    .index(indexName)
                    .query(query)
                    .size(limit), Map.class);
            return mapResults(response);
        } catch (IOException e) {
            throw new OpenSearchUnavailableException("Aday dokümanlar alınamadı", e);
        }
    }

    /**
     * Belirtilen ID listesine sahip dokümanları OpenSearch üzerinden toplu olarak sorgular.
     *
     * @param indexName Hedef indeks
     * @param ids Doküman ID listesi
     * @return Bulunan dokümanlar listesi
     */
    @SuppressWarnings("unchecked")
    public List<SearchResult> getDocumentsByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        try {
            SearchResponse<Map> response = client.search(s -> s
                    .index(indexName)
                    .query(q -> q.ids(i -> i.values(ids)))
                    .size(ids.size()), Map.class);
            return mapResults(response);
        } catch (Exception e) {
            log.warn("Failed to fetch documents by ids from {}: {}", indexName, e.getMessage());
            return List.of();
        }
    }

    /**
     * Varsayılan RRF parametreleri ile hibrit arama (BM25 + Vektör) çalıştırır.
     *
     * @param indexName Hedef indeks
     * @param queryText Metin sorgusu
     * @param queryVector Yoğun embedding vektörü
     * @param filters Filtreler
     * @param limit Sonuç adedi
     * @return Hibrit arama sonuçları
     */
    public List<SearchResult> hybridSearch(String indexName, String queryText,
                                            float[] queryVector, Map<String, Object> filters, int limit) {
        return hybridSearch(indexName, queryText, queryVector, filters, limit, 0);
    }

    /**
     * Sayfalama ötelemesi ile RRF hibrit arama çalıştırır.
     *
     * @param indexName Hedef indeks
     * @param queryText Metin sorgusu
     * @param queryVector Vektör
     * @param filters Filtreler
     * @param limit Sonuç adedi
     * @param offset Öteleme
     * @return Hibrit arama sonuçları
     */
    public List<SearchResult> hybridSearch(String indexName, String queryText,
                                            float[] queryVector, Map<String, Object> filters, int limit, int offset) {
        return hybridSearch(indexName, queryText, queryVector, filters, limit, offset, "RRF", 0.5, 0.5);
    }

    /**
     * Eşzamanlı (asenkron paralel) BM25 ve Vektör araması gerçekleştirip sonuçları seçilen
     * füzyon moduna (RRF veya Normalize Ağırlıklı Puanlama) göre birleştiren ana hibrit arama metodu.
     *
     * @param indexName Hedef indeks
     * @param queryText Metin sorgusu
     * @param queryVector Vektör
     * @param filters Filtreler
     * @param limit Dönecek sonuç sayısı
     * @param offset Sayfalama ötelemesi
     * @param fusionMode Füzyon modu ("RRF" veya "SCORE")
     * @param bm25Weight BM25 ağırlığı (puan füzyonu için)
     * @param semanticWeight Semantik ağırlık (puan füzyonu için)
     * @return Birleştirilmiş ve sıralanmış arama sonuçları
     */
    public List<SearchResult> hybridSearch(String indexName, String queryText,
                                            float[] queryVector, Map<String, Object> filters, int limit, int offset,
                                            String fusionMode, double bm25Weight, double semanticWeight) {
        int safeOffset = Math.max(0, offset);
        int fetchLimit = Math.min((limit + safeOffset) * 3, 100);

        java.util.concurrent.CompletableFuture<List<SearchResult>> bm25Future =
                java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        bm25Search(indexName, queryText, filters, fetchLimit, 0));

        java.util.concurrent.CompletableFuture<List<SearchResult>> vectorFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        vectorSearch(indexName, queryVector, filters, fetchLimit, 0));

        List<SearchResult> bm25Results = bm25Future.join();
        List<SearchResult> vectorResults = vectorFuture.join();

        List<SearchResult> fused;
        if ("RRF".equalsIgnoreCase(fusionMode)) {
            fused = applyReciprocalRankFusion(bm25Results, vectorResults, fetchLimit);
        } else {
            fused = applyNormalizedScoreFusion(bm25Results, vectorResults, bm25Weight, semanticWeight, fetchLimit);
        }

        if (safeOffset > 0) {
            return fused.stream()
                    .skip(safeOffset)
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        return fused.stream().limit(limit).collect(Collectors.toList());
    }

    // ---- Sağlık Durumu Kontrolü ----

    /**
     * OpenSearch kümesine ping atarak erişilebilirliğini kontrol eder.
     *
     * @return Küme sağlıklı ve erişilebilir ise true, aksi halde false
     */
    public boolean isHealthy() {
        try {
            return client.ping().value();
        } catch (Exception e) {
            log.warn("OpenSearch health check failed: {}", e.getMessage());
            return false;
        }
    }

    // ---- Füzyon Algoritmaları ----

    /**
     * Reciprocal Rank Fusion (RRF) formülü: score = 1 / (60 + rank + 1) uygulayarak
     * BM25 ve Vektör sıralamalarını tek bir nihai sıralamada birleştirir.
     *
     * @param bm25Results BM25 sonuçları
     * @param vectorResults Vektör sonuçları
     * @param limit Maksimum füzyon çıktısı adedi
     * @return RRF puanına göre sıralanmış sonuç listesi
     */
    private List<SearchResult> applyReciprocalRankFusion(List<SearchResult> bm25Results,
                                                          List<SearchResult> vectorResults,
                                                          int limit) {
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, SearchResult> resultMap = new HashMap<>();

        for (int rank = 0; rank < bm25Results.size(); rank++) {
            SearchResult result = bm25Results.get(rank);
            double score = 1.0 / (RRF_RANK_CONSTANT + rank + 1);
            rrfScores.merge(result.getId(), score, Double::sum);
            resultMap.putIfAbsent(result.getId(), result);
        }

        for (int rank = 0; rank < vectorResults.size(); rank++) {
            SearchResult result = vectorResults.get(rank);
            double score = 1.0 / (RRF_RANK_CONSTANT + rank + 1);
            rrfScores.merge(result.getId(), score, Double::sum);
            resultMap.putIfAbsent(result.getId(), result);
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    SearchResult result = resultMap.get(entry.getKey());
                    result.setScore(entry.getValue());
                    return result;
                })
                .collect(Collectors.toList());
    }

    /**
     * BM25 ve Vektör ham puanlarını maksimum puana bölerek normalize eder (0..1 aralığına çeker)
     * ve verilen ağırlıklara göre birleştirilmiş skor hesaplar.
     *
     * @param bm25Results BM25 sonuçları
     * @param vectorResults Vektör sonuçları
     * @param bm25Weight BM25 ağırlığı
     * @param semanticWeight Semantik arama ağırlığı
     * @param limit Sonuç limiti
     * @return Normalize ağırlıklı puanla sıralanmış liste
     */
    public List<SearchResult> applyNormalizedScoreFusion(List<SearchResult> bm25Results,
                                                          List<SearchResult> vectorResults,
                                                          double bm25Weight,
                                                          double semanticWeight,
                                                          int limit) {
        double weightTotal = bm25Weight + semanticWeight;
        double normBm25Weight = weightTotal > 0 ? bm25Weight / weightTotal : 0.5;
        double normSemanticWeight = weightTotal > 0 ? semanticWeight / weightTotal : 0.5;

        double maxBm25 = bm25Results.stream().mapToDouble(SearchResult::getScore).max().orElse(0.0);
        double maxVector = vectorResults.stream().mapToDouble(SearchResult::getScore).max().orElse(0.0);

        Map<String, SearchResult> resultMap = new HashMap<>();
        Map<String, Double> combinedScores = new HashMap<>();

        for (SearchResult result : bm25Results) {
            double normScore = maxBm25 > 0 ? Math.max(0.0, result.getScore()) / maxBm25 : 0.0;
            double contribution = normBm25Weight * normScore;
            combinedScores.merge(result.getId(), contribution, Double::sum);
            resultMap.putIfAbsent(result.getId(), result);
        }

        for (SearchResult result : vectorResults) {
            double normScore = maxVector > 0 ? Math.max(0.0, result.getScore()) / maxVector : 0.0;
            double contribution = normSemanticWeight * normScore;
            combinedScores.merge(result.getId(), contribution, Double::sum);
            resultMap.putIfAbsent(result.getId(), result);
        }

        return combinedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(entry -> {
                    SearchResult result = resultMap.get(entry.getKey());
                    result.setScore(entry.getValue());
                    return result;
                })
                .collect(Collectors.toList());
    }

    // ---- Sorgu Oluşturucular (Query Builders) ----

    /**
     * Türkçe alan ağırlıklandırmaları (title^3.0, birim^2.0, adres^2.0 vb.) ve
     * filtreleri barındıran BM25 BoolQuery'sini inşa eder.
     *
     * @param queryText Metin sorgusu
     * @param filters Filtreler
     * @return Yapılandırılmış OpenSearch Query nesnesi
     */
    private Query buildBm25QueryWithFilters(String queryText, Map<String, Object> filters) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        boolBuilder.must(m -> m
                .multiMatch(mm -> mm
                        .query(queryText)
                        .fields("title^3.0", "birim^2.0", "adres^2.0", "type^2.0", "shortText^2.0", "longText^2.0")
                        .analyzer("turkish_search")
                        .fuzziness("AUTO:5,8")
                )
        );

        addFilters(boolBuilder, filters);
        return Query.of(q -> q.bool(boolBuilder.build()));
    }

    /**
     * Filtre koşulları ile sınırlandırılmış k-NN vektör arama sorgusunu inşa eder.
     *
     * @param vectorList Gömme vektörü listesi
     * @param filters Filtre kriterleri
     * @param k En yakın komşu sayısı
     * @return Yapılandırılmış OpenSearch k-NN Query nesnesi
     */
    private Query buildVectorQueryWithFilters(List<Float> vectorList,
                                               Map<String, Object> filters, int k) {
        if (filters == null || filters.isEmpty()) {
            return Query.of(q -> q.knn(knn -> knn
                    .field("embedding")
                    .vector(vectorList)
                    .k(k)
            ));
        }

        BoolQuery.Builder filterBool = new BoolQuery.Builder();
        addFilters(filterBool, filters);
        Query filterQuery = Query.of(q -> q.bool(filterBool.build()));

        return Query.of(q -> q.knn(knn -> knn
                .field("embedding")
                .vector(vectorList)
                .k(k)
                .filter(filterQuery)
        ));
    }

    /**
     * Sorguya coğrafi konum (geo_distance), tarih aralığı ve alan bazlı terim filtrelerini ekler.
     *
     * @param boolBuilder OpenSearch boolean sorgu yapıcısı
     * @param filters Filtre kriterleri haritası
     */
    @SuppressWarnings("unchecked")
    private void addFilters(BoolQuery.Builder boolBuilder, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return;

        // 1. Coğrafi Mesafe Filtresi (konum alanı: lat, lon, radiusKm)
        if (filters.containsKey("lat") && filters.containsKey("lon")) {
            try {
                double lat = Double.parseDouble(filters.get("lat").toString());
                double lon = Double.parseDouble(filters.get("lon").toString());
                double radiusKm = 50.0;
                if (filters.containsKey("radiusKm")) {
                    radiusKm = Double.parseDouble(filters.get("radiusKm").toString());
                } else if (filters.containsKey("radius")) {
                    radiusKm = Double.parseDouble(filters.get("radius").toString());
                }
                final String distanceStr = radiusKm + "km";
                boolBuilder.filter(f -> f.geoDistance(g -> g
                        .field("konum")
                        .distance(distanceStr)
                        .location(loc -> loc.latlon(ll -> ll.lat(lat).lon(lon)))
                ));
            } catch (Exception e) {
                log.warn("Geo_distance filtresi uygulanamadı: {}", e.getMessage());
            }
        }

        // 2. Tarih Aralığı Filtresi (tarih alanı: startDate, endDate)
        if (filters.containsKey("startDate") || filters.containsKey("endDate")) {
            boolBuilder.filter(f -> f.range(r -> {
                var rb = r.field("tarih");
                if (filters.containsKey("startDate")) {
                    rb.gte(JsonData.of(filters.get("startDate").toString()));
                }
                if (filters.containsKey("endDate")) {
                    String end = filters.get("endDate").toString();
                    if (end.length() == 10) end = end + "T23:59:59.999Z";
                    rb.lte(JsonData.of(end));
                }
                return rb;
            }));
        }

        Set<String> specialHandledKeys = Set.of("lat", "lon", "radiusKm", "radius", "startDate", "endDate");
        Set<String> keywordSubfields = Set.of("type", "birim", "title", "adres");

        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String field = entry.getKey();
            if (specialHandledKeys.contains(field)) continue;

            Object value = entry.getValue();
            if (value == null) continue;

            String filterField = (keywordSubfields.contains(field) && !field.endsWith(".keyword"))
                    ? field + ".keyword"
                    : field;

            if (value instanceof List<?> listValue) {
                List<FieldValue> fieldValues = listValue.stream()
                        .map(v -> FieldValue.of(v.toString()))
                        .collect(Collectors.toList());
                boolBuilder.filter(f -> f.terms(t -> t
                        .field(filterField)
                        .terms(tv -> tv.value(fieldValues))
                ));
            } else if (value instanceof Map<?, ?> rangeParams) {
                Map<String, Object> rangeMap = (Map<String, Object>) rangeParams;
                boolBuilder.filter(f -> f.range(r -> {
                    var rb = r.field(field);
                    if (rangeMap.containsKey("gte")) rb.gte(JsonData.of(rangeMap.get("gte")));
                    if (rangeMap.containsKey("gt")) rb.gt(JsonData.of(rangeMap.get("gt")));
                    if (rangeMap.containsKey("lte")) {
                        Object lteVal = rangeMap.get("lte");
                        if (lteVal != null && field.equals("tarih") && lteVal.toString().length() == 10) {
                            rb.lte(JsonData.of(lteVal.toString() + "T23:59:59.999Z"));
                        } else {
                            rb.lte(JsonData.of(lteVal));
                        }
                    }
                    if (rangeMap.containsKey("lt")) rb.lt(JsonData.of(rangeMap.get("lt")));
                    return rb;
                }));
            } else {
                boolBuilder.filter(f -> f.term(t -> t
                        .field(filterField)
                        .value(FieldValue.of(value.toString()))
                ));
            }
        }
    }

    // ---- Eşleme Yardımcıları (Mapping Helpers) ----

    /**
     * OpenSearch arama yanıtındaki tüm isabetleri (hits) SearchResult listesine dönüştürür.
     *
     * @param response OpenSearch ham yanıtı
     * @return Dönüştürülmüş sonuç listesi
     */
    @SuppressWarnings("unchecked")
    private List<SearchResult> mapResults(SearchResponse<Map> response) {
        return response.hits().hits().stream()
                .map(this::mapHit)
                .collect(Collectors.toList());
    }

    /**
     * Tek bir OpenSearch isabetini SearchResult nesnesine dönüştürür.
     *
     * @param hit OpenSearch hit nesnesi
     * @return SearchResult DTO'su
     */
    @SuppressWarnings("unchecked")
    private SearchResult mapHit(Hit<Map> hit) {
        Map<String, Object> source = hit.source();
        if (source == null) return new SearchResult();
        SearchResult result = mapSource(source);
        result.setScore(hit.score() != null ? hit.score() : 0.0);
        return result;
    }

    /**
     * Ham kaynak haritasını (source map) SearchResult DTO'suna eşler.
     *
     * @param source Doküman kaynak haritası
     * @return SearchResult nesnesi
     */
    @SuppressWarnings("unchecked")
    public SearchResult mapSource(Map<String, Object> source) {
        if (source == null) return new SearchResult();

        SearchResult result = new SearchResult();
        result.setId((String) source.get("id"));
        result.setType((String) source.get("type"));
        result.setTitle((String) source.get("title"));
        result.setSearchText((String) source.get("searchText"));
        result.setShortText((String) source.get("shortText"));
        result.setLongText((String) source.get("longText"));
        result.setBirim((String) source.get("birim"));
        result.setAdres((String) source.get("adres"));
        result.setTarih((String) source.get("tarih"));
        result.setKonum(source.get("konum"));

        Object metadata = source.get("metadata");
        if (metadata instanceof Map<?, ?>) {
            result.setMetadata((Map<String, Object>) metadata);
        }

        String createdAt = (String) source.get("createdAt");
        if (createdAt != null) {
            try { result.setCreatedAt(Instant.parse(createdAt)); } catch (Exception ignored) {}
        }

        String updatedAt = (String) source.get("updatedAt");
        if (updatedAt != null) {
            try { result.setUpdatedAt(Instant.parse(updatedAt)); } catch (Exception ignored) {}
        }

        Map<String, Object> structured = new HashMap<>();
        for (Map.Entry<String, Object> e : source.entrySet()) {
            if (!CORE_FIELDS.contains(e.getKey())) {
                structured.put(e.getKey(), e.getValue());
            }
        }
        if (!structured.isEmpty()) result.setStructuredFields(structured);

        return result;
    }

    /**
     * İlkel float dizisini Float nesneleri listesine dönüştürür.
     *
     * @param array İlkel float dizisi
     * @return Float listesi
     */
    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) list.add(v);
        return list;
    }
}
