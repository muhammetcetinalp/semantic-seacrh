package com.example.semantic_search.controller;

import com.example.semantic_search.dto.IndexDocumentRequest;
import com.example.semantic_search.service.IndexingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Doküman indeksleme, güncelleme, silme ve toplu indeksleme API uç noktalarını sunan REST denetleyicisi.
 *
 * <p>Uç noktalar:
 * <ul>
 *   <li>{@code POST /api/v1/index}: Tekil doküman ekler.</li>
 *   <li>{@code PUT /api/v1/index/{id}}: Var olan dokümanı günceller.</li>
 *   <li>{@code DELETE /api/v1/index/{id}}: Dokümanı indeksten siler.</li>
 *   <li>{@code POST /api/v1/index/bulk}: Çoklu doküman listesini toplu indeksler.</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/index")
public class IndexingController {

    private final IndexingService indexingService;

    /**
     * IndexingController bağımlılığını enjekte eden yapıcı metot.
     *
     * @param indexingService İndeksleme iş mantığı servisi
     */
    public IndexingController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    /**
     * Yeni bir dokümanı dizine ekler.
     *
     * @param request İndekslenecek dokümanın alanlarını içeren DTO
     * @return 201 Created durum kodu ve doküman ID'si
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> indexDocument(
            @Valid @RequestBody IndexDocumentRequest request) {
        indexingService.indexDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "indexed", "id", request.getId()));
    }

    /**
     * Belirtilen ID'ye sahip dokümanı günceller.
     *
     * @param id Güncellenecek dokümanın benzersiz kimliği
     * @param request Güncellenmiş doküman verileri
     * @return 200 OK yanıtı
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> updateDocument(
            @PathVariable String id,
            @Valid @RequestBody IndexDocumentRequest request) {
        indexingService.updateDocument(id, request);
        return ResponseEntity.ok(Map.of("status", "updated", "id", id));
    }

    /**
     * Belirtilen ID'ye sahip dokümanı indeksten siler.
     *
     * @param id Silinecek dokümanın ID'si
     * @param indexName Hedef indeks adı (opsiyonel)
     * @return 200 OK yanıtı
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteDocument(
            @PathVariable String id,
            @RequestParam(required = false) String indexName) {
        indexingService.deleteDocument(indexName, id);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
    }

    /**
     * Çoklu doküman listesini toplu olarak (bulk) indeksler.
     *
     * @param requests İndekslenecek doküman istekleri listesi
     * @return 201 Created durum kodu ve indekslenen doküman sayısı
     */
    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulkIndex(
            @Valid @RequestBody List<IndexDocumentRequest> requests) {
        indexingService.bulkIndex(requests);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "indexed", "count", requests.size()));
    }
}
