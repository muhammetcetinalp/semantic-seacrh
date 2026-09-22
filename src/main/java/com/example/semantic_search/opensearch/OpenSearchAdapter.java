package com.example.semantic_search.opensearch;

import com.example.semantic_search.configuration.EmbeddingProperties;
import com.example.semantic_search.exception.DocumentNotFoundException;
import com.example.semantic_search.exception.OpenSearchUnavailableException;
import com.example.semantic_search.indexing.SearchDocument;
import com.example.semantic_search.search.SearchResult;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.json.JsonData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter that isolates all OpenSearch interactions.
 * The rest of the application never touches OpenSearch APIs directly.
 */
@Component
public class OpenSearchAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchAdapter.class);
    private static final int RRF_RANK_CONSTANT = 60;
    private static final Set<String> CORE_FIELDS = Set.of(
            "id", "type", "title", "searchText", "tags",
            "metadata", "embedding", "createdAt", "updatedAt",
            "shortText", "longText", "birim", "adres", "tarih", "konum"
    );

    private final OpenSearchClient client;
    private final EmbeddingProperties embeddingProperties;
    private final OpenSearchDocumentSourceMapper documentSourceMapper;

    public OpenSearchAdapter(OpenSearchClient client, EmbeddingProperties embeddingProperties,
                             OpenSearchDocumentSourceMapper documentSourceMapper) {
        this.client = client;
        this.embeddingProperties = embeddingProperties;
        this.documentSourceMapper = documentSourceMapper;
    }

    // ---- Index management ----

    public void createIndexIfNotExists(String indexName) {
        try {
            boolean exists = client.indices().exists(e -> e.index(indexName)).value();
            if (!exists) {
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
                                                                "turkish_stop", "turkish_stemmer")))
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
                                .properties("type", p -> p.keyword(k -> k))
                                .properties("title", p -> p.text(t -> t
                                        .analyzer("turkish_search")
                                        .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))))
                                .properties("searchText", p -> p.text(t -> t
                                        .analyzer("turkish_search")))
                                .properties("shortText", p -> p.text(t -> t
                                        .analyzer("turkish_search")))
                                .properties("longText", p -> p.text(t -> t
                                        .analyzer("turkish_search")))
                                .properties("birim", p -> p.keyword(k -> k))
                                .properties("adres", p -> p.text(t -> t
                                        .analyzer("turkish_search")
                                        .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))))
                                .properties("tarih", p -> p.date(d -> d))
                                .properties("konum", p -> p.geoPoint(gp -> gp))
                                .properties("tags", p -> p.keyword(k -> k))
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
            throw new OpenSearchUnavailableException("Failed to create index: " + indexName, e);
        }
    }

    // ---- Document CRUD ----

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
            throw new OpenSearchUnavailableException("Failed to index document: " + document.getId(), e);
        }
    }

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
            throw new OpenSearchUnavailableException("Bulk indexing failed", e);
        }
    }

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
            throw new OpenSearchUnavailableException("Failed to delete document: " + documentId, e);
        }
    }

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
            throw new OpenSearchUnavailableException("Failed to get document: " + documentId, e);
        }
    }

    // ---- Search operations ----

    @SuppressWarnings("unchecked")
    public List<SearchResult> bm25Search(String indexName, String queryText,
                                          Map<String, Object> filters, int limit) {
        return bm25Search(indexName, queryText, filters, limit, 0);
    }

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
            throw new OpenSearchUnavailableException("BM25 search failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<SearchResult> vectorSearch(String indexName, float[] queryVector,
                                            Map<String, Object> filters, int limit) {
        return vectorSearch(indexName, queryVector, filters, limit, 0);
    }

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
            throw new OpenSearchUnavailableException("Vector search failed", e);
        }
    }

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
            throw new OpenSearchUnavailableException("Failed to fetch candidates", e);
        }
    }

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

    public List<SearchResult> hybridSearch(String indexName, String queryText,
                                            float[] queryVector, Map<String, Object> filters, int limit) {
        return hybridSearch(indexName, queryText, queryVector, filters, limit, 0);
    }

    public List<SearchResult> hybridSearch(String indexName, String queryText,
                                            float[] queryVector, Map<String, Object> filters, int limit, int offset) {
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

        List<SearchResult> fused = applyReciprocalRankFusion(bm25Results, vectorResults, fetchLimit);
        if (safeOffset > 0) {
            return fused.stream()
                    .skip(safeOffset)
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        return fused.stream().limit(limit).collect(Collectors.toList());
    }

    // ---- Health ----

    public boolean isHealthy() {
        try {
            return client.ping().value();
        } catch (Exception e) {
            log.warn("OpenSearch health check failed: {}", e.getMessage());
            return false;
        }
    }

    // ---- RRF ----

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

    // ---- Query builders ----

    private Query buildBm25QueryWithFilters(String queryText, Map<String, Object> filters) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        boolBuilder.must(m -> m
                .multiMatch(mm -> mm
                        .query(queryText)
                        .fields("title^2", "shortText^1.5", "searchText", "longText", "adres", "birim", "tags^1.5")
                        .analyzer("turkish_search")
                        .fuzziness("AUTO")
                        .minimumShouldMatch("75%")
                )
        );

        addFilters(boolBuilder, filters);

        return Query.of(q -> q.bool(boolBuilder.build()));
    }

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

    @SuppressWarnings("unchecked")
    private void addFilters(BoolQuery.Builder boolBuilder, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return;

        // 1. Geo-distance filter on 'konum' (lat, lon, radiusKm/radius)
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
                log.warn("Failed to apply geo_distance filter: {}", e.getMessage());
            }
        }

        // 2. Date range filter on 'tarih' (startDate, endDate)
        if (filters.containsKey("startDate") || filters.containsKey("endDate")) {
            boolBuilder.filter(f -> f.range(r -> {
                var rb = r.field("tarih");
                if (filters.containsKey("startDate")) {
                    rb.gte(JsonData.of(filters.get("startDate").toString()));
                }
                if (filters.containsKey("endDate")) {
                    rb.lte(JsonData.of(filters.get("endDate").toString()));
                }
                return rb;
            }));
        }

        Set<String> specialHandledKeys = Set.of("lat", "lon", "radiusKm", "radius", "startDate", "endDate");

        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String field = entry.getKey();
            if (specialHandledKeys.contains(field)) continue;

            Object value = entry.getValue();
            if (value == null) continue;

            if (value instanceof List<?> listValue) {
                List<FieldValue> fieldValues = listValue.stream()
                        .map(v -> FieldValue.of(v.toString()))
                        .collect(Collectors.toList());
                boolBuilder.filter(f -> f.terms(t -> t
                        .field(field)
                        .terms(tv -> tv.value(fieldValues))
                ));
            } else if (value instanceof Map<?, ?> rangeParams) {
                Map<String, Object> rangeMap = (Map<String, Object>) rangeParams;
                boolBuilder.filter(f -> f.range(r -> {
                    var rb = r.field(field);
                    if (rangeMap.containsKey("gte")) rb.gte(JsonData.of(rangeMap.get("gte")));
                    if (rangeMap.containsKey("gt")) rb.gt(JsonData.of(rangeMap.get("gt")));
                    if (rangeMap.containsKey("lte")) rb.lte(JsonData.of(rangeMap.get("lte")));
                    if (rangeMap.containsKey("lt")) rb.lt(JsonData.of(rangeMap.get("lt")));
                    return rb;
                }));
            } else {
                boolBuilder.filter(f -> f.term(t -> t
                        .field(field)
                        .value(FieldValue.of(value.toString()))
                ));
            }
        }
    }

    // ---- Mapping helpers ----

    @SuppressWarnings("unchecked")
    private List<SearchResult> mapResults(SearchResponse<Map> response) {
        return response.hits().hits().stream()
                .map(this::mapHit)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private SearchResult mapHit(Hit<Map> hit) {
        Map<String, Object> source = hit.source();
        if (source == null) return new SearchResult();
        SearchResult result = mapSource(source);
        result.setScore(hit.score() != null ? hit.score() : 0.0);
        return result;
    }

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

        Object tags = source.get("tags");
        if (tags instanceof List<?>) {
            result.setTags(((List<?>) tags).stream().map(Object::toString).collect(Collectors.toList()));
        }

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

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) list.add(v);
        return list;
    }
}
