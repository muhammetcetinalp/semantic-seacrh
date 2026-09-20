package com.example.semantic_search.qdrant;

import com.example.semantic_search.configuration.ColbertProperties;
import com.example.semantic_search.configuration.QdrantProperties;
import com.example.semantic_search.search.ColbertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/colbert")
public class ColbertSyncController {

    private final ColbertIndexingSyncService syncService;
    private final QdrantAdapter qdrantAdapter;
    private final ColbertService colbertService;
    private final ColbertProperties colbertProperties;
    private final QdrantProperties qdrantProperties;

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

    @RequestMapping(value = "/sync", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Map<String, Object>> sync(@RequestParam(required = false, defaultValue = "entities") String indexName) {
        Map<String, Object> result = syncService.syncAllFromOpenSearch(indexName);
        return ResponseEntity.ok(result);
    }

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
