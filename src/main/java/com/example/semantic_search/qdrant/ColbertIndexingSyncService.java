package com.example.semantic_search.qdrant;

import com.example.semantic_search.configuration.ColbertProperties;
import com.example.semantic_search.configuration.QdrantProperties;
import com.example.semantic_search.opensearch.OpenSearchAdapter;
import com.example.semantic_search.search.ColbertService;
import com.example.semantic_search.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ColbertIndexingSyncService {

    private static final Logger log = LoggerFactory.getLogger(ColbertIndexingSyncService.class);

    private final OpenSearchAdapter openSearchAdapter;
    private final ColbertService colbertService;
    private final QdrantAdapter qdrantAdapter;
    private final ColbertProperties colbertProperties;
    private final QdrantProperties qdrantProperties;
    private final com.example.semantic_search.configuration.SearchProperties searchProperties;

    public ColbertIndexingSyncService(OpenSearchAdapter openSearchAdapter,
                                      ColbertService colbertService,
                                      QdrantAdapter qdrantAdapter,
                                      ColbertProperties colbertProperties,
                                      QdrantProperties qdrantProperties,
                                      com.example.semantic_search.configuration.SearchProperties searchProperties) {
        this.openSearchAdapter = openSearchAdapter;
        this.colbertService = colbertService;
        this.qdrantAdapter = qdrantAdapter;
        this.colbertProperties = colbertProperties;
        this.qdrantProperties = qdrantProperties;
        this.searchProperties = searchProperties;
    }

    public Map<String, Object> syncAllFromOpenSearch(String indexName) {
        long start = System.currentTimeMillis();
        if (!colbertProperties.isEnabled() || !qdrantProperties.isEnabled()) {
            return Map.of("status", "skipped", "reason", "ColBERT or Qdrant is disabled in configuration");
        }

        if (!qdrantAdapter.isAvailable()) {
            return Map.of("status", "error", "reason", "Qdrant is not reachable at " + qdrantProperties.getEndpoint());
        }

        if (!colbertService.isAvailable()) {
            return Map.of("status", "error", "reason", "ColBERT service is not reachable at " + colbertProperties.getEndpoint());
        }

        String resolvedIndex = (indexName != null && !indexName.isBlank()) ? indexName : searchProperties.getDefaultIndexName();
        List<SearchResult> docs = openSearchAdapter.getCandidates(resolvedIndex, Map.of(), 10000);

        if (docs == null || docs.isEmpty()) {
            log.warn("No documents found in OpenSearch index '{}' to sync into Qdrant", resolvedIndex);
            return Map.of("status", "ok", "syncedCount", 0, "message", "No documents found in OpenSearch");
        }

        log.info("Starting one-time ColBERT sync for {} documents into Qdrant collection '{}'...",
                docs.size(), qdrantProperties.getCollectionName());

        List<QdrantPoint> points = new ArrayList<>();
        int successCount = 0;

        for (SearchResult doc : docs) {
            String title = doc.getTitle() != null ? doc.getTitle() : "";
            String text = doc.getSearchText() != null ? doc.getSearchText() : "";

            List<List<Float>> vectors = colbertService.embedDocument(doc.getId(), title, text);
            if (vectors != null && !vectors.isEmpty()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("title", title);
                payload.put("searchText", text);
                payload.put("tags", doc.getTags() != null ? doc.getTags() : List.of());
                payload.put("type", doc.getType() != null ? doc.getType() : "OLAY");
                if (doc.getShortText() != null) payload.put("shortText", doc.getShortText());
                if (doc.getLongText() != null) payload.put("longText", doc.getLongText());
                if (doc.getBirim() != null) payload.put("birim", doc.getBirim());
                if (doc.getAdres() != null) payload.put("adres", doc.getAdres());
                if (doc.getTarih() != null) payload.put("tarih", doc.getTarih());
                if (doc.getKonum() != null) payload.put("konum", doc.getKonum());

                points.add(new QdrantPoint(doc.getId(), vectors, payload));
                successCount++;
            }
        }

        boolean saved = qdrantAdapter.upsertPoints(points);
        long tookMs = System.currentTimeMillis() - start;

        log.info("Qdrant ColBERT sync completed: {}/{} docs embedded and stored in {}ms (success={})",
                successCount, docs.size(), tookMs, saved);

        return Map.of(
                "status", saved ? "ok" : "partial_error",
                "collection", qdrantProperties.getCollectionName(),
                "totalFound", docs.size(),
                "syncedCount", successCount,
                "tookMs", tookMs
        );
    }

    public void indexSingleDocument(String id, String title, String text, Map<String, Object> payload) {
        if (!colbertProperties.isEnabled() || !qdrantProperties.isEnabled()) {
            return;
        }
        if (!qdrantAdapter.isAvailable() || !colbertService.isAvailable()) {
            return;
        }
        try {
            List<List<Float>> vectors = colbertService.embedDocument(id, title, text);
            if (vectors != null && !vectors.isEmpty()) {
                qdrantAdapter.upsertPoint(id, vectors, payload != null ? payload : Map.of());
            }
        } catch (Exception e) {
            log.warn("Async ColBERT document indexing to Qdrant failed for id {}: {}", id, e.getMessage());
        }
    }

    public void deleteSingleDocument(String id) {
        if (!colbertProperties.isEnabled() || !qdrantProperties.isEnabled()) {
            return;
        }
        if (!qdrantAdapter.isAvailable()) {
            return;
        }
        try {
            qdrantAdapter.deletePoint(id);
        } catch (Exception e) {
            log.warn("ColBERT document deletion from Qdrant failed for id {}: {}", id, e.getMessage());
        }
    }
}
