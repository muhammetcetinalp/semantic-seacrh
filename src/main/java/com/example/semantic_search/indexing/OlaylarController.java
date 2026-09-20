package com.example.semantic_search.indexing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/olaylar")
public class OlaylarController {

    private final OlaylarIngestionService ingestionService;

    public OlaylarController(OlaylarIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @RequestMapping(value = "/import", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Map<String, Object>> importOlaylar(
            @RequestParam(required = false, defaultValue = "1000") int limit,
            @RequestParam(required = false, defaultValue = "false") boolean enableDenseEmbedding) {
        Map<String, Object> result = ingestionService.ingest(limit, enableDenseEmbedding);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/meta")
    public ResponseEntity<Map<String, Object>> getMeta() {
        return ResponseEntity.ok(ingestionService.getMetadata());
    }
}
