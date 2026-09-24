package com.example.semantic_search.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Kök (Root) ve servis durumu uç noktalarını sunan REST denetleyicisi.
 */
@RestController
@CrossOrigin
public class RootController {

    @GetMapping({"/", "/api"})
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "service", "Semantic Search Engine",
                "status", "UP",
                "version", "0.0.1-SNAPSHOT",
                "endpoints", Map.of(
                        "search", "/api/v1/search",
                        "searchExplain", "/api/v1/search/explain",
                        "indexing", "/api/v1/index",
                        "indexCount", "/api/v1/index/count",
                        "dataImport", "/api/v1/data/import",
                        "dataBulkJson", "/api/v1/data/bulk-json",
                        "dataMeta", "/api/v1/data/meta",
                        "olaylarImport", "/api/v1/olaylar/import",
                        "olaylarMeta", "/api/v1/olaylar/meta",
                        "simulation", "/api/v1/simulation/kafka/status"
                )
        ));
    }
}
