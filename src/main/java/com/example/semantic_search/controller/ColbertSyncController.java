package com.example.semantic_search.controller;

import com.example.semantic_search.client.qdrant.QdrantAdapter;
import com.example.semantic_search.config.ColbertProperties;
import com.example.semantic_search.config.QdrantProperties;
import com.example.semantic_search.service.ColbertIndexingSyncService;
import com.example.semantic_search.service.ColbertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ColBERT ve Qdrant entegrasyon durumunu sorgulayan ve OpenSearch-Qdrant
 * senkronizasyonunu tetikleyen REST denetleyicisi.
 */
@RestController
@RequestMapping("/api/v1/colbert")
public class ColbertSyncController {

    private final ColbertIndexingSyncService syncService;
    private final QdrantAdapter qdrantAdapter;
    private final ColbertService colbertService;
    private final ColbertProperties colbertProperties;
    private final QdrantProperties qdrantProperties;

    /**
     * ColbertSyncController bileşenini yapılandıran yapıcı metot.
     *
     * @param syncService ColBERT senkronizasyon servisi
     * @param qdrantAdapter Qdrant adaptörü
     * @param colbertService ColBERT servisi
     * @param colbertProperties ColBERT yapılandırma özellikleri
     * @param qdrantProperties Qdrant yapılandırma özellikleri
     */
    public ColbertSyncController(ColbertIndexingSyncService syncService,
                                 QdrantAdapter qdrantAdapter,
                                 ColbertService colbertService,
                                 ColbertProperties colbertProperties,
                                 QdrantProperties qdrantProperties) {
        this.syncService = syncService;
        this.qdrantAdapter = qdrantAdapter;
        this.colbertService = colbertService;
        this.colbertProperties = colbertProperties;
        this.qdrantProperties = qdrantProperties;
    }

    /**
     * OpenSearch dokümanlarını Qdrant çoklu-vektör koleksiyonuna aktaran senkronizasyon işlemini tetikler.
     *
     * @param indexName Hedef indeks adı (varsayılan: entities)
     * @return Senkronizasyon sonuç raporu
     */
    @RequestMapping(value = "/sync", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Map<String, Object>> sync(@RequestParam(required = false, defaultValue = "entities") String indexName) {
        Map<String, Object> result = syncService.syncAllFromOpenSearch(indexName);
        return ResponseEntity.ok(result);
    }

    /**
     * ColBERT motoru ve Qdrant veritabanının güncel sağlık ve yapılandırma durumunu döner.
     *
     * @return Durum ve yapılandırma parametreleri haritası
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        boolean qdrantUp = qdrantAdapter.isAvailable();
        boolean colbertUp = colbertService.isAvailable();
        return ResponseEntity.ok(Map.of(
                "colbertEnabled", colbertProperties.isEnabled(),
                "colbertStorage", colbertProperties.getStorage(),
                "colbertServiceUp", colbertUp,
                "qdrantEnabled", qdrantProperties.isEnabled(),
                "qdrantCollection", qdrantProperties.getCollectionName(),
                "qdrantServiceUp", qdrantUp
        ));
    }
}
