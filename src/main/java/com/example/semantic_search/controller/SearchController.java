package com.example.semantic_search.controller;

import com.example.semantic_search.dto.HybridExplainRequest;
import com.example.semantic_search.dto.HybridExplainResponse;
import com.example.semantic_search.dto.SearchRequest;
import com.example.semantic_search.dto.SearchResponse;
import com.example.semantic_search.service.SearchQueryService;
import com.example.semantic_search.model.SearchType;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Arama motoru API uç noktalarını (endpoints) sunan REST denetleyicisi.
 *
 * <p>Aşağıdaki uç noktaları yönetir:
 * <ul>
 *   <li>{@code POST /api/v1/search}: BM25, Vektör (Dense) veya Hibrit arama sorgularını çalıştırır.</li>
 *   <li>{@code GET /api/v1/search}: Basit arama sorgularını query parametreleri ile çalıştırır.</li>
 *   <li>{@code POST /api/v1/search/explain}: Çok aşamalı hibrit arama (BM25 + Vektör + Füzyon + Reranker)
 *       analizini ve aşama detaylarını döner.</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/search")
@CrossOrigin
public class SearchController {

    private final SearchQueryService searchQueryService;

    /**
     * SearchController bağımlılığını enjekte eden yapıcı metot.
     *
     * @param searchQueryService Arama sorgulama iş mantığı servisi
     */
    @Autowired
    public SearchController(SearchQueryService searchQueryService) {
        this.searchQueryService = searchQueryService;
    }

    /**
     * Standart arama sorgusunu POST gövdesi ile çalıştırır.
     *
     * @param request Arama kriterlerini, filtreleri ve sayfalama parametrelerini içeren DTO
     * @return Eşleşen dokümanlar ve yürütme metriklerini içeren HTTP 200 yanıtı
     */
    @PostMapping
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        SearchResponse response = searchQueryService.search(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Basit arama sorgusunu GET parametreleri ile çalıştırır.
     *
     * @param query Arama sorgusu
     * @param indexName İndeks adı (opsiyonel)
     * @param searchType Arama türü (opsiyonel: BM25, SEMANTIC, HYBRID)
     * @param limit Sonuç limiti (varsayılan: 10)
     * @param offset Sayfalama başlangıcı (varsayılan: 0)
     * @return Arama sonuçları
     */
    @GetMapping
    public ResponseEntity<SearchResponse> searchGet(
            @RequestParam String query,
            @RequestParam(required = false) String indexName,
            @RequestParam(required = false) SearchType searchType,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset) {
        SearchRequest request = new SearchRequest();
        request.setQuery(query);
        request.setIndexName(indexName);
        request.setSearchType(searchType);
        request.setLimit(limit);
        request.setOffset(offset);
        return ResponseEntity.ok(searchQueryService.search(request));
    }

    /**
     * Hibrit aramanın her bir aşamasını (BM25 puanları, Semantik Vektör benzerlikleri,
     * RRF/Score füzyonu ve Cross-Encoder yeniden sıralaması) derinlemesine analiz eden açıklama uç noktası.
     *
     * @param request Hibrit analiz parametreleri
     * @return Detaylı aşama dökümünü içeren HTTP 200 yanıtı
     */
    @PostMapping("/explain")
    public ResponseEntity<HybridExplainResponse> explainHybrid(
            @Valid @RequestBody HybridExplainRequest request) {
        return ResponseEntity.ok(searchQueryService.explainHybrid(request));
    }
}
