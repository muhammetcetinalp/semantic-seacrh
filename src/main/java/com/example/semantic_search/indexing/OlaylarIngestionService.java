package com.example.semantic_search.indexing;

import com.example.semantic_search.embedding.EmbeddingProvider;
import com.example.semantic_search.opensearch.OpenSearchAdapter;
import com.example.semantic_search.qdrant.QdrantAdapter;
import com.example.semantic_search.qdrant.QdrantPoint;
import com.example.semantic_search.search.ColbertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

@Service
public class OlaylarIngestionService {

    private static final Logger log = LoggerFactory.getLogger(OlaylarIngestionService.class);

    private final OpenSearchAdapter openSearchAdapter;
    private final ColbertService colbertService;
    private final QdrantAdapter qdrantAdapter;
    private final EmbeddingProvider embeddingProvider;
    private final ObjectMapper objectMapper;
    private final IndexingStateRepository indexingStateRepository;
    private final com.example.semantic_search.opensearch.OpenSearchDocumentSourceMapper documentSourceMapper;

    @Value("${search.defaults.default-index-name:olaylar}")
    private String defaultIndexName;

    public OlaylarIngestionService(OpenSearchAdapter openSearchAdapter,
                                   ColbertService colbertService,
                                   QdrantAdapter qdrantAdapter,
                                   EmbeddingProvider embeddingProvider,
                                   ObjectMapper objectMapper,
                                   IndexingStateRepository indexingStateRepository,
                                   com.example.semantic_search.opensearch.OpenSearchDocumentSourceMapper documentSourceMapper) {
        this.openSearchAdapter = openSearchAdapter;
        this.colbertService = colbertService;
        this.qdrantAdapter = qdrantAdapter;
        this.embeddingProvider = embeddingProvider;
        this.objectMapper = objectMapper;
        this.indexingStateRepository = indexingStateRepository;
        this.documentSourceMapper = documentSourceMapper;
    }

    public File locateOlaylarFile() {
        Path p1 = Paths.get("src/main/java/com/example/semantic_search/olaylar.json");
        if (Files.exists(p1)) return p1.toFile();

        Path p2 = Paths.get("olaylar.json");
        if (Files.exists(p2)) return p2.toFile();

        Path p3 = Paths.get("src/main/resources/olaylar.json");
        if (Files.exists(p3)) return p3.toFile();

        return null;
    }

    public Map<String, Object> getMetadata() {
        File file = locateOlaylarFile();
        if (file == null) {
            return Map.of("error", "olaylar.json not found");
        }

        Set<String> types = new TreeSet<>();
        Set<String> birims = new TreeSet<>();
        int total = 0;

        try (InputStream is = new FileInputStream(file)) {
            JsonNode root = objectMapper.readTree(is);
            if (root.isArray()) {
                total = root.size();
                for (JsonNode node : root) {
                    JsonNode fields = node.path("fields");
                    String type = fields.path("type").asText(null);
                    if (type != null && !type.isBlank()) types.add(type);

                    String birim = fields.path("birim").asText(null);
                    if (birim != null && !birim.isBlank()) birims.add(birim);
                }
            }
        } catch (Exception e) {
            log.error("Failed to read metadata from olaylar.json: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }

        return Map.of(
                "totalRecords", total,
                "types", new ArrayList<>(types),
                "birims", new ArrayList<>(birims)
        );
    }

    public Map<String, Object> ingest(int limit, boolean enableDenseEmbedding) {
        long start = System.currentTimeMillis();
        File file = locateOlaylarFile();
        if (file == null) {
            return Map.of("status", "error", "message", "olaylar.json not found in project directory");
        }

        String indexName = defaultIndexName != null && !defaultIndexName.isBlank() ? defaultIndexName : "olaylar";
        openSearchAdapter.createIndexIfNotExists(indexName);

        int totalRead = 0;
        int indexedCount = 0;
        int batchSize = 100;

        List<SearchDocument> osBatch = new ArrayList<>(batchSize);
        List<QdrantPoint> qdrantBatch = new ArrayList<>(batchSize);

        try (InputStream is = new FileInputStream(file)) {
            JsonNode root = objectMapper.readTree(is);
            if (!root.isArray()) {
                return Map.of("status", "error", "message", "olaylar.json does not contain an array");
            }

            int targetLimit = (limit > 0) ? Math.min(limit, root.size()) : root.size();
            log.info("Starting ingestion of {} incidents into OpenSearch index '{}' and Qdrant...", targetLimit, indexName);

            for (int i = 0; i < targetLimit; i++) {
                JsonNode node = root.get(i);
                totalRead++;

                String entityId = node.path("entityId").asText(UUID.randomUUID().toString());
                String title = node.path("title").asText("");
                String shortText = node.path("shortText").asText("");
                String longText = node.path("longText").asText("");

                JsonNode fields = node.path("fields");
                String type = fields.path("type").asText("OLAY");
                String birim = fields.path("birim").asText("");
                String adres = fields.path("adres").asText("");
                String tarih = fields.path("tarih").asText(null);
                String konumStr = fields.path("konum").asText(null);

                // Combined search text for rich multi-field BM25 & semantic matching
                StringBuilder sb = new StringBuilder();
                if (!title.isBlank()) sb.append(title).append("\n\n");
                if (!shortText.isBlank()) sb.append(shortText).append("\n\n");
                if (!longText.isBlank()) sb.append(longText).append("\n\n");
                if (!birim.isBlank()) sb.append("Birim: ").append(birim).append("\n");
                if (!adres.isBlank()) sb.append("Adres: ").append(adres);
                String fullSearchText = sb.toString().trim();

                float[] denseEmbedding = null;
                if (enableDenseEmbedding) {
                    try {
                        denseEmbedding = embeddingProvider.generateEmbedding(fullSearchText.substring(0, Math.min(500, fullSearchText.length())));
                    } catch (Exception e) {
                        log.debug("Dense embedding generation skipped for {}: {}", entityId, e.getMessage());
                    }
                }

                SearchDocument doc = SearchDocument.builder()
                        .id(entityId)
                        .indexName(indexName)
                        .type(type)
                        .title(title)
                        .searchText(fullSearchText)
                        .shortText(shortText)
                        .longText(longText)
                        .birim(birim)
                        .adres(adres)
                        .tarih(tarih)
                        .konum(konumStr)
                        .tags(List.of(type, birim))
                        .metadata(Map.of(
                                "fieldId", fields.path("fieldId").asText(""),
                                "version", node.path("version").asInt(1),
                                "timestamp", node.path("timestamp").asText("")
                        ))
                        .embedding(denseEmbedding)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

                osBatch.add(doc);

                // Prepare Qdrant multi-vector point
                if (colbertService.isAvailable() && qdrantAdapter.isAvailable()) {
                    List<List<Float>> multiVectors = colbertService.embedDocument(entityId, title, shortText + " " + longText);
                    if (multiVectors != null && !multiVectors.isEmpty()) {
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("title", title);
                        payload.put("searchText", fullSearchText);
                        payload.put("shortText", shortText);
                        payload.put("longText", longText);
                        payload.put("type", type);
                        payload.put("birim", birim);
                        payload.put("adres", adres);
                        payload.put("tarih", tarih);
                        payload.put("tags", List.of(type, birim));
                        qdrantBatch.add(new QdrantPoint(entityId, multiVectors, payload));
                    }
                }

                if (osBatch.size() >= batchSize) {
                    openSearchAdapter.bulkIndex(osBatch);
                    saveOracleState(osBatch, indexName);
                    if (!qdrantBatch.isEmpty()) {
                        qdrantAdapter.upsertPoints(qdrantBatch);
                        qdrantBatch.clear();
                    }
                    indexedCount += osBatch.size();
                    osBatch.clear();
                    log.info("Ingested {} / {} incidents (took {}ms)...", indexedCount, targetLimit, System.currentTimeMillis() - start);
                }
            }

            // Flush remaining
            if (!osBatch.isEmpty()) {
                openSearchAdapter.bulkIndex(osBatch);
                saveOracleState(osBatch, indexName);
                if (!qdrantBatch.isEmpty()) {
                    qdrantAdapter.upsertPoints(qdrantBatch);
                    qdrantBatch.clear();
                }
                indexedCount += osBatch.size();
                osBatch.clear();
            }

        } catch (Exception e) {
            log.error("Ingestion failed: {}", e.getMessage(), e);
            return Map.of("status", "error", "message", e.getMessage(), "indexedSoFar", indexedCount);
        }

        long took = System.currentTimeMillis() - start;
        log.info("Successfully ingested {}/{} incidents into '{}' in {}ms", indexedCount, totalRead, indexName, took);

        return Map.of(
                "status", "ok",
                "indexName", indexName,
                "totalRead", totalRead,
                "indexedCount", indexedCount,
                "tookMs", took
        );
    }

    private void saveOracleState(List<SearchDocument> docs, String indexName) {
        if (indexingStateRepository == null || docs == null || docs.isEmpty()) return;
        try {
            for (SearchDocument doc : docs) {
                IndexingState state = indexingStateRepository
                        .findByDocumentIdAndIndexName(doc.getId(), indexName)
                        .orElseGet(() -> {
                            IndexingState s = new IndexingState();
                            s.setDocumentId(doc.getId());
                            s.setIndexName(indexName);
                            return s;
                        });
                state.setStatus(IndexingStatus.INDEXED);
                state.setSearchTextHash(hash(doc.getSearchText()));
                if (documentSourceMapper != null) {
                    state.setDocumentSource(documentSourceMapper.toJson(doc));
                }
                indexingStateRepository.save(state);
            }
        } catch (Exception e) {
            log.warn("Failed to persist indexing state to Oracle during ingestion: {}", e.getMessage());
        }
    }

    private String hash(String text) {
        if (text == null) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }
}
