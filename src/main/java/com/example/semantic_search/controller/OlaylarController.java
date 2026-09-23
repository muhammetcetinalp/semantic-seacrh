package com.example.semantic_search.controller;

import com.example.semantic_search.service.OlaylarIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * {@code olaylar.json} veri kümesinin içe aktarımı (ingestion) ve meta veri analizini
 * yöneten REST denetleyicisi.
 */
@RestController
@RequestMapping("/api/v1/olaylar")
public class OlaylarController {

    private final OlaylarIngestionService ingestionService;

    /**
     * OlaylarController bağımlılığını enjekte eden yapıcı metot.
     *
     * @param ingestionService Olay aktarım servisi
     */
    public OlaylarController(OlaylarIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * {@code olaylar.json} dosyasındaki verileri OpenSearch sistemine aktarır.
     *
     * @param limit Aktarılacak kayıt sayısı (varsayılan: 1000)
     * @param enableDenseEmbedding Yoğun vektör üretimi aktif mi (varsayılan: false)
     * @param recreateIndex İndeks sıfırdan yeniden oluşturulsun mu (varsayılan: false)
     * @return Aktarım durum haritası
     */
    @RequestMapping(value = "/import", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Map<String, Object>> importOlaylar(
            @RequestParam(required = false, defaultValue = "1000") int limit,
            @RequestParam(required = false, defaultValue = "false") boolean enableDenseEmbedding,
            @RequestParam(required = false, defaultValue = "false") boolean recreateIndex) {
        Map<String, Object> result = ingestionService.ingest(limit, enableDenseEmbedding, recreateIndex);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code olaylar.json} dosyasının kayıt sayısı, benzersiz türleri ve birimlerini döner.
     *
     * @return Meta veri bilgileri haritası
     */
    @GetMapping("/meta")
    public ResponseEntity<Map<String, Object>> getMeta() {
        return ResponseEntity.ok(ingestionService.getMetadata());
    }
}
