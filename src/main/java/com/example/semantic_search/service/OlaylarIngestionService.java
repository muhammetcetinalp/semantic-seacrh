package com.example.semantic_search.service;

import com.example.semantic_search.client.embedding.EmbeddingProvider;
import com.example.semantic_search.client.opensearch.OpenSearchAdapter;
import com.example.semantic_search.client.opensearch.OpenSearchDocumentSourceMapper;
import com.example.semantic_search.client.qdrant.QdrantAdapter;
import com.example.semantic_search.model.IndexingState;
import com.example.semantic_search.model.IndexingStatus;
import com.example.semantic_search.model.QdrantPoint;
import com.example.semantic_search.model.SearchDocument;
import com.example.semantic_search.repository.IndexingStateRepository;
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

/**
 * {@code olaylar.json} veri dosyasını okuyarak olay kayıtlarını OpenSearch'e,
 * PostgreSQL indeksleme durum tablosuna ve Qdrant çoklu-vektör koleksiyonuna
 * toplu olarak aktaran içe alma (ingestion / ETL) servisi.
 */
@Service
public class OlaylarIngestionService {

    private static final Logger log = LoggerFactory.getLogger(OlaylarIngestionService.class);

    private final OpenSearchAdapter openSearchAdapter;
    private final ColbertService colbertService;
    private final QdrantAdapter qdrantAdapter;
    private final EmbeddingProvider embeddingProvider;
    private final ObjectMapper objectMapper;
    private final IndexingStateRepository indexingStateRepository;
    private final OpenSearchDocumentSourceMapper documentSourceMapper;

    @Value("${search.defaults.default-index-name:olaylar}")
    private String defaultIndexName;

    /**
     * OlaylarIngestionService bileşenini yapılandıran yapıcı metot.
     *
     * @param openSearchAdapter OpenSearch istemci adaptörü
     * @param colbertService ColBERT servisi
     * @param qdrantAdapter Qdrant adaptörü
     * @param embeddingProvider Vektör sağlayıcı
     * @param objectMapper Jackson JSON dönüştürücü
     * @param indexingStateRepository İndeksleme durum tablosu deposu
     * @param documentSourceMapper Doküman kaynak dönüştürücüsü
     */
    public OlaylarIngestionService(OpenSearchAdapter openSearchAdapter,
                                   ColbertService colbertService,
                                   QdrantAdapter qdrantAdapter,
                                   EmbeddingProvider embeddingProvider,
                                   ObjectMapper objectMapper,
                                   IndexingStateRepository indexingStateRepository,
                                   OpenSearchDocumentSourceMapper documentSourceMapper) {
        this.openSearchAdapter = openSearchAdapter;
        this.colbertService = colbertService;
        this.qdrantAdapter = qdrantAdapter;
        this.embeddingProvider = embeddingProvider;
        this.objectMapper = objectMapper;
        this.indexingStateRepository = indexingStateRepository;
        this.documentSourceMapper = documentSourceMapper;
    }

    /**
     * {@code olaylar.json} dosyasını proje dizininde veya kaynak yollarında arayarak bulur.
     *
     * @return Bulunan File nesnesi veya bulunamazsa null
     */
    public File locateOlaylarFile() {
        Path p1 = Paths.get("src/main/java/com/example/semantic_search/olaylar.json");
        if (Files.exists(p1)) return p1.toFile();

        Path p2 = Paths.get("olaylar.json");
        if (Files.exists(p2)) return p2.toFile();

        Path p3 = Paths.get("src/main/resources/olaylar.json");
        if (Files.exists(p3)) return p3.toFile();

        return null;
    }

    /**
     * {@code olaylar.json} dosyasını tarayarak toplam kayıt adedi, benzersiz olay tipleri
     * ve birim listesi gibi özet meta verileri döner.
     *
     * @return Meta veri haritası
     */
    public Map<String, Object> getMetadata() {
        File file = locateOlaylarFile();
        if (file == null) {
            return Map.of("error", "olaylar.json bulunamadı");
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
            log.error("olaylar.json dosyasından meta veri okunamadı: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }

        return Map.of(
                "totalRecords", total,
                "types", new ArrayList<>(types),
                "birims", new ArrayList<>(birims)
        );
    }

    /**
     * Olay kayıtlarını varsayılan olarak indeksi yeniden oluşturmadan aktarır.
     *
     * @param limit Aktarılacak maksimum kayıt sayısı (0 veya negatif ise tümü)
     * @param enableDenseEmbedding Yoğun vektör (dense embedding) üretiminin yapılıp yapılmayacağı
     * @return İçe aktarma sonuç raporu
     */
    public Map<String, Object> ingest(int limit, boolean enableDenseEmbedding) {
        return ingest(limit, enableDenseEmbedding, false);
    }

    /**
     * {@code olaylar.json} dosyasındaki verileri OpenSearch, PostgreSQL ve Qdrant'a aktaran ana metot.
     *
     * @param limit Aktarılacak kayıt sayısı limiti
     * @param enableDenseEmbedding Dense embedding üretimi bayrağı
     * @param recreateIndex İndeksin silinip sıfırdan oluşturulup oluşturulmayacağı
     * @return Aktarım durum haritası
     */
    public Map<String, Object> ingest(int limit, boolean enableDenseEmbedding, boolean recreateIndex) {
        long start = System.currentTimeMillis();
        File file = locateOlaylarFile();
        if (file == null) {
            return Map.of("status", "error", "message", "olaylar.json proje dizininde bulunamadı");
        }

        String indexName = defaultIndexName != null && !defaultIndexName.isBlank() ? defaultIndexName : "olaylar";
        if (recreateIndex) {
            openSearchAdapter.deleteIndex(indexName);
        }
        openSearchAdapter.createIndexIfNotExists(indexName);

        int totalRead = 0;
        int indexedCount = 0;
        int batchSize = 100;

        List<SearchDocument> osBatch = new ArrayList<>(batchSize);
        List<QdrantPoint> qdrantBatch = new ArrayList<>(batchSize);

        try (InputStream is = new FileInputStream(file)) {
            JsonNode root = objectMapper.readTree(is);
            if (!root.isArray()) {
                return Map.of("status", "error", "message", "olaylar.json geçerli bir dizi içermiyor");
            }

            int targetLimit = (limit > 0) ? Math.min(limit, root.size()) : root.size();
            log.info("{} adet olayın OpenSearch '{}' indeksi ve Qdrant'a aktarımı başlatılıyor (recreate={})...",
                    targetLimit, indexName, recreateIndex);

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

                // Anlamsal olarak zenginleştirilmiş temiz metin blokunu inşa et
                StringBuilder sb = new StringBuilder();
                if (!title.isBlank()) sb.append(title).append("\n\n");
                if (!type.isBlank()) sb.append("Olay Tipi: ").append(type).append("\n");
                if (!birim.isBlank()) sb.append("Birim: ").append(birim).append("\n");
                if (!adres.isBlank()) sb.append("Konum: ").append(adres).append("\n\n");
                if (!shortText.isBlank()) sb.append("Özet: ").append(shortText).append("\n\n");
                if (!longText.isBlank() && !longText.equals(shortText)) sb.append("Detay: ").append(longText);
                String fullSearchText = sb.toString().trim();

                float[] denseEmbedding = null;
                if (enableDenseEmbedding && !fullSearchText.isBlank()) {
                    try {
                        denseEmbedding = embeddingProvider.generateEmbedding(fullSearchText);
                    } catch (Exception e) {
                        log.debug("Doküman ({}) için dense embedding üretimi atlandı: {}", entityId, e.getMessage());
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

                // Qdrant ColBERT noktası hazırla
                if (colbertService.isAvailable() && qdrantAdapter.isAvailable()) {
                    List<List<Float>> multiVectors = colbertService.embedDocument(entityId, title, fullSearchText);
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
                        qdrantBatch.add(new QdrantPoint(entityId, multiVectors, payload));
                    }
                }

                if (osBatch.size() >= batchSize) {
                    openSearchAdapter.bulkIndex(osBatch);
                    saveIndexingState(osBatch, indexName);
                    if (!qdrantBatch.isEmpty()) {
                        qdrantAdapter.upsertPoints(qdrantBatch);
                        qdrantBatch.clear();
                    }
                    indexedCount += osBatch.size();
                    osBatch.clear();
                    log.info("{} / {} olay aktarıldı ({}ms)...", indexedCount, targetLimit, System.currentTimeMillis() - start);
                }
            }

            // Kalan dokümanları yaz
            if (!osBatch.isEmpty()) {
                openSearchAdapter.bulkIndex(osBatch);
                saveIndexingState(osBatch, indexName);
                if (!qdrantBatch.isEmpty()) {
                    qdrantAdapter.upsertPoints(qdrantBatch);
                    qdrantBatch.clear();
                }
                indexedCount += osBatch.size();
                osBatch.clear();
            }

        } catch (Exception e) {
            log.error("İçe aktarım başarısız: {}", e.getMessage(), e);
            return Map.of("status", "error", "message", e.getMessage(), "indexedSoFar", indexedCount);
        }

        long took = System.currentTimeMillis() - start;
        log.info("{} adet olay başarıyla '{}' indeksine aktarıldı (süre: {}ms)", indexedCount, indexName, took);

        return Map.of(
                "status", "ok",
                "indexName", indexName,
                "totalRead", totalRead,
                "indexedCount", indexedCount,
                "tookMs", took
        );
    }

    /**
     * Toplu indekslenen dokümanların durumunu PostgreSQL veritabanına kaydeder.
     *
     * @param docs Dokümanlar listesi
     * @param indexName İndeks adı
     */
    private void saveIndexingState(List<SearchDocument> docs, String indexName) {
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
            log.warn("İçe aktarım sırasında PostgreSQL indeksleme durumu kaydedilemedi: {}", e.getMessage());
        }
    }

    /**
     * SHA-256 hash hesaplama yardımcısı.
     *
     * @param text Metin
     * @return Hex dizgisi
     */
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
