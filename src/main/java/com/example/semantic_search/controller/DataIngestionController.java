package com.example.semantic_search.controller;

import com.example.semantic_search.service.DataIngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Airgap ve yerel JSON veri kümelerinin içe aktarımı (ingestion), doğrudan JSON dökümü yükleme
 * ve meta veri analizini yöneten REST denetleyicisi.
 *
 * <p>Hem {@code /api/v1/data} (jenerik) hem de {@code /api/v1/olaylar} (geriye dönük uyumlu)
 * yollarını destekler.</p>
 */
@RestController
@RequestMapping({"/api/v1/data", "/api/v1/olaylar"})
@CrossOrigin
public class DataIngestionController {

    private final DataIngestionService ingestionService;

    /**
     * DataIngestionController bağımlılığını enjekte eden yapıcı metot.
     *
     * @param ingestionService Veri aktarım servisi
     */
    @Autowired
    public DataIngestionController(DataIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Dosyadaki (data.json, dataset.json veya olaylar.json) verileri OpenSearch sistemine aktarır.
     *
     * @param limit Aktarılacak kayıt sayısı (varsayılan: 1000)
     * @param enableDenseEmbedding Yoğun vektör üretimi aktif mi (varsayılan: false)
     * @param recreateIndex İndeks sıfırdan yeniden oluşturulsun mu (varsayılan: false)
     * @param filePath Özel dosya yolu (opsiyonel)
     * @return Aktarım durum haritası
     */
    @RequestMapping(value = "/import", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Map<String, Object>> importData(
            @RequestParam(required = false, defaultValue = "1000") int limit,
            @RequestParam(required = false, defaultValue = "false") boolean enableDenseEmbedding,
            @RequestParam(required = false, defaultValue = "false") boolean recreateIndex,
            @RequestParam(required = false) String filePath) {
        Map<String, Object> result = ingestionService.ingest(filePath, limit, enableDenseEmbedding, recreateIndex);
        return ResponseEntity.ok(result);
    }

    /**
     * Airgap ortamındaki veritabanı veya sistemden doğrudan HTTP POST ile gönderilen
     * ham JSON dizisini toplu olarak OpenSearch'e aktarır.
     *
     * @param jsonContent İstek gövdesinde gönderilen ham JSON metni
     * @param limit Aktarılacak kayıt sayısı limiti (0 ise tümü)
     * @param enableDenseEmbedding Vektör üretimi yapılsın mı
     * @param recreateIndex İndeks yeniden oluşturulsun mu
     * @param indexName Hedef indeks adı (opsiyonel)
     * @return Aktarım durum haritası
     */
    @PostMapping("/bulk-json")
    public ResponseEntity<Map<String, Object>> importDirectJson(
            @RequestBody String jsonContent,
            @RequestParam(required = false, defaultValue = "0") int limit,
            @RequestParam(required = false, defaultValue = "false") boolean enableDenseEmbedding,
            @RequestParam(required = false, defaultValue = "false") boolean recreateIndex,
            @RequestParam(required = false) String indexName) {
        Map<String, Object> result = ingestionService.ingestDirectJson(
                jsonContent, limit, enableDenseEmbedding, recreateIndex, indexName);
        return ResponseEntity.ok(result);
    }

    /**
     * Veri dosyasının kayıt sayısı, benzersiz türleri ve birimlerini döner.
     *
     * @param filePath Özel dosya yolu (opsiyonel)
     * @return Meta veri bilgileri haritası
     */
    @GetMapping("/meta")
    public ResponseEntity<Map<String, Object>> getMeta(@RequestParam(required = false) String filePath) {
        return ResponseEntity.ok(ingestionService.getMetadata(filePath));
    }
}
