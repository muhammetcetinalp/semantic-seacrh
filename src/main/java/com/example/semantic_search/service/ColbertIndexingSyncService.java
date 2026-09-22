package com.example.semantic_search.service;

import com.example.semantic_search.client.opensearch.OpenSearchAdapter;
import com.example.semantic_search.client.qdrant.QdrantAdapter;
import com.example.semantic_search.config.ColbertProperties;
import com.example.semantic_search.config.QdrantProperties;
import com.example.semantic_search.config.SearchProperties;
import com.example.semantic_search.dto.SearchResult;
import com.example.semantic_search.model.QdrantPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * OpenSearch üzerindeki dokümanlar ile Qdrant çoklu-vektör koleksiyonu arasındaki
 * ColBERT token vektör senkronizasyonunu yöneten servis.
 *
 * <p>İlk açılışta veya yönetimsel tetiklemelerde OpenSearch indeksini tarayarak
 * eksik çoklu vektörleri hesaplar ve Qdrant koleksiyonuna aktarır. Ayrıca doküman ekleme
 * ve silme işlemlerinde artımlı (incremental) senkronizasyon sağlar.</p>
 */
@Service
public class ColbertIndexingSyncService {

    private static final Logger log = LoggerFactory.getLogger(ColbertIndexingSyncService.class);

    private final OpenSearchAdapter openSearchAdapter;
    private final ColbertService colbertService;
    private final QdrantAdapter qdrantAdapter;
    private final ColbertProperties colbertProperties;
    private final QdrantProperties qdrantProperties;
    private final SearchProperties searchProperties;

    /**
     * ColbertIndexingSyncService bileşenini yapılandıran yapıcı metot.
     *
     * @param openSearchAdapter OpenSearch istemci adaptörü
     * @param colbertService ColBERT gömme ve sıralama servisi
     * @param qdrantAdapter Qdrant istemci adaptörü
     * @param colbertProperties ColBERT yapılandırma özellikleri
     * @param qdrantProperties Qdrant yapılandırma özellikleri
     * @param searchProperties Genel arama yapılandırma özellikleri
     */
    public ColbertIndexingSyncService(OpenSearchAdapter openSearchAdapter,
                                      ColbertService colbertService,
                                      QdrantAdapter qdrantAdapter,
                                      ColbertProperties colbertProperties,
                                      QdrantProperties qdrantProperties,
                                      SearchProperties searchProperties) {
        this.openSearchAdapter = openSearchAdapter;
        this.colbertService = colbertService;
        this.qdrantAdapter = qdrantAdapter;
        this.colbertProperties = colbertProperties;
        this.qdrantProperties = qdrantProperties;
        this.searchProperties = searchProperties;
    }

    /**
     * Belirtilen OpenSearch indeksindeki tüm dokümanları okur, ColBERT çoklu vektörlerini üretir
     * ve toplu olarak Qdrant koleksiyonuna yazar.
     *
     * @param indexName Hedef OpenSearch indeks adı (boşsa varsayılan indeks kullanılır)
     * @return Senkronizasyon durum raporu haritası
     */
    public Map<String, Object> syncAllFromOpenSearch(String indexName) {
        long start = System.currentTimeMillis();
        if (!colbertProperties.isEnabled() || !qdrantProperties.isEnabled()) {
            return Map.of("status", "skipped", "reason", "ColBERT veya Qdrant yapılandırmada devre dışı bırakılmış");
        }

        if (!qdrantAdapter.isAvailable()) {
            return Map.of("status", "error", "reason", "Qdrant servisine erişilemiyor: " + qdrantProperties.getEndpoint());
        }

        if (!colbertService.isAvailable()) {
            return Map.of("status", "error", "reason", "ColBERT servisine erişilemiyor: " + colbertProperties.getEndpoint());
        }

        String resolvedIndex = (indexName != null && !indexName.isBlank()) ? indexName : searchProperties.getDefaultIndexName();
        List<SearchResult> docs = openSearchAdapter.getCandidates(resolvedIndex, Map.of(), 10000);

        if (docs == null || docs.isEmpty()) {
            log.warn("Qdrant'a aktarılacak doküman OpenSearch indeksinde ('{}') bulunamadı", resolvedIndex);
            return Map.of("status", "ok", "syncedCount", 0, "message", "OpenSearch indeksinde doküman bulunamadı");
        }

        log.info("Qdrant '{}' koleksiyonuna {} doküman için ColBERT senkronizasyonu başlatılıyor...",
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

        log.info("Qdrant ColBERT senkronizasyonu tamamlandı: {}/{} doküman {}ms sürede aktarıldı (başarı={})",
                successCount, docs.size(), tookMs, saved);

        return Map.of(
                "status", saved ? "ok" : "partial_error",
                "collection", qdrantProperties.getCollectionName(),
                "totalFound", docs.size(),
                "syncedCount", successCount,
                "tookMs", tookMs
        );
    }

    /**
     * Tek bir doküman eklendiğinde veya güncellendiğinde ColBERT vektörlerini hesaplayıp Qdrant'a yazar.
     *
     * @param id Doküman ID'si
     * @param title Doküman başlığı
     * @param text Doküman arama metni
     * @param payload İlişkili meta veriler
     */
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
            log.warn("Qdrant'a tekil ColBERT indekslemesi başarısız (id={}): {}", id, e.getMessage());
        }
    }

    /**
     * Doküman silindiğinde Qdrant üzerindeki ColBERT vektör noktasını da siler.
     *
     * @param id Silinecek dokümanın benzersiz kimliği
     */
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
            log.warn("Qdrant'tan tekil ColBERT silme başarısız (id={}): {}", id, e.getMessage());
        }
    }
}
