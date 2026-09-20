package com.example.semantic_search.search;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchQueryService searchQueryService;

    public SearchController(SearchQueryService searchQueryService) {
        this.searchQueryService = searchQueryService;
    }

    @PostMapping
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        SearchResponse response = searchQueryService.search(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/explain")
    public ResponseEntity<HybridExplainResponse> explainHybrid(
            @Valid @RequestBody HybridExplainRequest request) {
        return ResponseEntity.ok(searchQueryService.explainHybrid(request));
    }
}
