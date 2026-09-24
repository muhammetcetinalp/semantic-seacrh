package com.example.semantic_search.service;

import com.example.semantic_search.client.embedding.EmbeddingProvider;
import com.example.semantic_search.client.opensearch.OpenSearchAdapter;
import com.example.semantic_search.client.opensearch.OpenSearchDocumentSourceMapper;
import com.example.semantic_search.model.IndexingState;
import com.example.semantic_search.model.IndexingStatus;
import com.example.semantic_search.model.SearchDocument;
import com.example.semantic_search.repository.IndexingStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * Airgap ve yerel ortamlardaki her türlü JSON veri kümesini okuyarak
 * OpenSearch arama motoruna ve dahili indeksleme durum tablosuna aktaran jenerik ETL servisi.
 */
@Service
public class DataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DataIngestionService.class);

    private final OpenSearchAdapter openSearchAdapter;
    private final EmbeddingProvider embeddingProvider;
    private final ObjectMapper objectMapper;
    private final IndexingStateRepository indexingStateRepository;
    private final OpenSearchDocumentSourceMapper documentSourceMapper;

    @Value("${search.defaults.default-index-name:olaylar}")
    private String defaultIndexName;

    /**
     * DataIngestionService bileşenini yapılandıran yapıcı metot.
     *
     * @param openSearchAdapter OpenSearch istemci adaptörü
     * @param embeddingProvider Vektör sağlayıcı
     * @param objectMapper Jackson JSON dönüştürücü
     * @param indexingStateRepository İndeksleme durum tablosu deposu
     * @param documentSourceMapper Doküman kaynak dönüştürücüsü
     */
    @Autowired
    public DataIngestionService(OpenSearchAdapter openSearchAdapter,
                                EmbeddingProvider embeddingProvider,
                                ObjectMapper objectMapper,
                                IndexingStateRepository indexingStateRepository,
                                OpenSearchDocumentSourceMapper documentSourceMapper) {
        this.openSearchAdapter = openSearchAdapter;
        this.embeddingProvider = embeddingProvider;
        this.objectMapper = objectMapper;
        this.indexingStateRepository = indexingStateRepository;
        this.documentSourceMapper = documentSourceMapper;
    }

    /**
     * Veri dosyasını (data.json, dataset.json, olaylar.json) standart dizinlerde arar.
     *
     * @return Bulunan File nesnesi veya bulunamazsa null
     */
    public File locateDataFile() {
        return locateDataFile(null);
    }

    /**
     * Belirtilen özel yolda veya standart proje dizinlerinde veri dosyasını arar.
     *
     * @param customPath Kullanıcı tarafından belirtilen özel dosya yolu
     * @return Bulunan File nesnesi veya null
     */
    public File locateDataFile(String customPath) {
        if (customPath != null && !customPath.isBlank()) {
            Path cp = Paths.get(customPath);
            if (Files.exists(cp)) return cp.toFile();
        }

        String[] candidateNames = {"data.json", "dataset.json", "olaylar.json"};
        String[] candidateDirs = {
                "",
                "src/main/resources/",
                "src/main/java/com/example/semantic_search/"
        };

        for (String name : candidateNames) {
            for (String dir : candidateDirs) {
                Path p = Paths.get(dir + name);
                if (Files.exists(p)) return p.toFile();
            }
        }

        return null;
    }

    /**
     * Geriye dönük uyumluluk için locateOlaylarFile takma adı.
     */
    public File locateOlaylarFile() {
        return locateDataFile();
    }

    /**
     * Veri dosyasını tarayarak toplam kayıt adedi ve benzersiz türler gibi özet meta verileri döner.
     *
     * @return Meta veri haritası
     */
    public Map<String, Object> getMetadata() {
        return getMetadata(null);
    }

    /**
     * Belirtilen dosya yolundan veri dosyasını tarayarak özet meta verileri döner.
     *
     * @param customPath Özel dosya yolu (opsiyonel)
     * @return Meta veri haritası
     */
    public Map<String, Object> getMetadata(String customPath) {
        File file = locateDataFile(customPath);
        if (file == null) {
            return Map.of("error", "Veri dosyası (data.json / dataset.json / olaylar.json) bulunamadı");
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
                    String type = fields.path("type").asText(node.path("type").asText(null));
                    if (type != null && !type.isBlank()) types.add(type);

                    String birim = fields.path("birim").asText(node.path("birim").asText(node.path("unit").asText(null)));
                    if (birim != null && !birim.isBlank()) birims.add(birim);
                }
            }
        } catch (Exception e) {
            log.error("Veri dosyasından meta veri okunamadı: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }

        return Map.of(
                "fileName", file.getName(),
                "totalRecords", total,
                "types", new ArrayList<>(types),
                "birims", new ArrayList<>(birims)
        );
    }

    /**
     * Varsayılan dosyadan verileri aktarır.
     *
     * @param limit Aktarılacak maksimum kayıt sayısı
     * @param enableDenseEmbedding Yoğun vektör (dense embedding) üretim bayrağı
     * @return İçe aktarma sonuç raporu
     */
    public Map<String, Object> ingest(int limit, boolean enableDenseEmbedding) {
        return ingest(limit, enableDenseEmbedding, false);
    }

    /**
     * Varsayılan dosyadan verileri aktarır (indeks sıfırlama seçeneğiyle).
     *
     * @param limit Aktarılacak kayıt sayısı limiti
     * @param enableDenseEmbedding Dense embedding üretimi bayrağı
     * @param recreateIndex İndeksin silinip sıfırdan oluşturulup oluşturulmayacağı
     * @return Aktarım durum haritası
     */
    public Map<String, Object> ingest(int limit, boolean enableDenseEmbedding, boolean recreateIndex) {
        return ingest(null, limit, enableDenseEmbedding, recreateIndex);
    }

    /**
     * Belirtilen dosya yolundan veya varsayılan dosyalardan verileri aktarır.
     *
     * @param customPath Özel dosya yolu (opsiyonel)
     * @param limit Aktarılacak kayıt sayısı limiti
     * @param enableDenseEmbedding Dense embedding üretimi bayrağı
     * @param recreateIndex İndeksin silinip sıfırdan oluşturulup oluşturulmayacağı
     * @return Aktarım durum haritası
     */
    public Map<String, Object> ingest(String customPath, int limit, boolean enableDenseEmbedding, boolean recreateIndex) {
        File file = locateDataFile(customPath);
        if (file == null) {
            return Map.of("status", "error", "message", "Veri dosyası diskte bulunamadı");
        }

        try (InputStream is = new FileInputStream(file)) {
            JsonNode root = objectMapper.readTree(is);
            if (!root.isArray()) {
                return Map.of("status", "error", "message", "Veri dosyası geçerli bir JSON dizisi içermiyor");
            }
            return processJsonNodes(root, limit, enableDenseEmbedding, recreateIndex, defaultIndexName);
        } catch (Exception e) {
            log.error("İçe aktarım başarısız: {}", e.getMessage(), e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /**
     * Airgap ortamındaki sunucudan doğrudan HTTP POST ile gelen ham JSON dökümünü toplu olarak aktarır.
     *
     * @param jsonContent Ham JSON dizisi
     * @param limit Aktarılacak kayıt sayısı limiti (0 ise tümü)
     * @param enableDenseEmbedding Dense embedding üretimi bayrağı
     * @param recreateIndex İndeksin silinip sıfırdan oluşturulup oluşturulmayacağı
     * @param targetIndexName Hedef indeks adı (opsiyonel, boşsa varsayılan)
     * @return Aktarım sonuç haritası
     */
    public Map<String, Object> ingestDirectJson(String jsonContent, int limit, boolean enableDenseEmbedding,
                                               boolean recreateIndex, String targetIndexName) {
        try {
            JsonNode root = objectMapper.readTree(jsonContent);
            if (!root.isArray()) {
                return Map.of("status", "error", "message", "Gönderilen veri geçerli bir JSON dizisi içermelidir.");
            }
            String index = (targetIndexName != null && !targetIndexName.isBlank()) ? targetIndexName : defaultIndexName;
            return processJsonNodes(root, limit, enableDenseEmbedding, recreateIndex, index);
        } catch (Exception e) {
            log.error("Doğrudan JSON içe aktarımı başarısız: {}", e.getMessage(), e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /**
     * JSON düğümlerini ayrıştırıp OpenSearch'e toplu indeksleyen çekirdek metod.
     */
    private Map<String, Object> processJsonNodes(JsonNode root, int limit, boolean enableDenseEmbedding,
                                                boolean recreateIndex, String indexName) {
        long start = System.currentTimeMillis();
        String targetIndex = (indexName != null && !indexName.isBlank()) ? indexName : "olaylar";

        if (recreateIndex) {
            openSearchAdapter.deleteIndex(targetIndex);
        }
        openSearchAdapter.createIndexIfNotExists(targetIndex);

        int totalRead = 0;
        int indexedCount = 0;
        int batchSize = 100;
        List<SearchDocument> osBatch = new ArrayList<>(batchSize);

        int targetLimit = (limit > 0) ? Math.min(limit, root.size()) : root.size();
        log.info("{} adet verinin OpenSearch '{}' indeksine aktarımı başlatılıyor (recreate={})...",
                targetLimit, targetIndex, recreateIndex);

        for (int i = 0; i < targetLimit; i++) {
            JsonNode node = root.get(i);
            totalRead++;

            // Kimlik çözümleme (hem entityId, hem id, hem documentId desteklenir)
            String entityId = node.path("entityId").asText(
                    node.path("id").asText(
                            node.path("documentId").asText(UUID.randomUUID().toString())));

            // Başlık ve metin alanları
            String title = node.path("title").asText(node.path("name").asText(node.path("baslik").asText("")));
            String shortText = node.path("shortText").asText(node.path("summary").asText(node.path("ozet").asText("")));
            String longText = node.path("longText").asText(node.path("content").asText(
                    node.path("text").asText(node.path("detail").asText(""))));

            JsonNode fields = node.path("fields");
            String type = fields.path("type").asText(node.path("type").asText(node.path("category").asText("DOCUMENT")));
            String birim = fields.path("birim").asText(node.path("birim").asText(node.path("unit").asText("")));
            String adres = fields.path("adres").asText(node.path("adres").asText(node.path("location").asText(node.path("address").asText(""))));
            String tarih = fields.path("tarih").asText(node.path("tarih").asText(node.path("date").asText(null)));
            String konumStr = fields.path("konum").asText(node.path("konum").asText(null));

            // Anlamsal olarak zenginleştirilmiş temiz arama metnini inşa et
            StringBuilder sb = new StringBuilder();
            if (!title.isBlank()) sb.append(title).append("\n\n");
            if (!type.isBlank()) sb.append("Kategori: ").append(type).append("\n");
            if (!birim.isBlank()) sb.append("Birim: ").append(birim).append("\n");
            if (!adres.isBlank()) sb.append("Konum: ").append(adres).append("\n\n");
            if (!shortText.isBlank()) sb.append("Özet: ").append(shortText).append("\n\n");
            if (!longText.isBlank() && !longText.equals(shortText)) sb.append("Detay: ").append(longText);

            String fullSearchText = sb.toString().trim();
            if (fullSearchText.isBlank() && !title.isBlank()) {
                fullSearchText = title;
            }

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
                    .indexName(targetIndex)
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

            if (osBatch.size() >= batchSize) {
                openSearchAdapter.bulkIndex(osBatch);
                saveIndexingState(osBatch, targetIndex);
                indexedCount += osBatch.size();
                osBatch.clear();
                log.info("{} / {} kayıt aktarıldı ({}ms)...", indexedCount, targetLimit, System.currentTimeMillis() - start);
            }
        }

        // Kalan dokümanları yaz
        if (!osBatch.isEmpty()) {
            openSearchAdapter.bulkIndex(osBatch);
            saveIndexingState(osBatch, targetIndex);
            indexedCount += osBatch.size();
            osBatch.clear();
        }

        long took = System.currentTimeMillis() - start;
        log.info("{} adet kayıt başarıyla '{}' indeksine aktarıldı (süre: {}ms)", indexedCount, targetIndex, took);

        return Map.of(
                "status", "ok",
                "indexName", targetIndex,
                "totalRead", totalRead,
                "indexedCount", indexedCount,
                "tookMs", took
        );
    }

    /**
     * Toplu indekslenen dokümanların durumunu dahili durum deposuna kaydeder.
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
            log.warn("İçe aktarım sırasında indeksleme durumu kaydedilemedi: {}", e.getMessage());
        }
    }

    /**
     * SHA-256 hash hesaplama yardımcısı.
     */
    private String hash(String text) {
        if (text == null) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }
}
