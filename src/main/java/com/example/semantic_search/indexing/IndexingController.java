package com.example.semantic_search.indexing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/index")
public class IndexingController {

    private final IndexingService indexingService;

    public IndexingController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> indexDocument(
            @Valid @RequestBody IndexDocumentRequest request) {
        indexingService.indexDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "indexed", "id", request.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> updateDocument(
            @PathVariable String id,
            @Valid @RequestBody IndexDocumentRequest request) {
        indexingService.updateDocument(id, request);
        return ResponseEntity.ok(Map.of("status", "updated", "id", id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteDocument(
            @PathVariable String id,
            @RequestParam(required = false) String indexName) {
        indexingService.deleteDocument(indexName, id);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
    }

    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulkIndex(
            @Valid @RequestBody List<IndexDocumentRequest> requests) {
        indexingService.bulkIndex(requests);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "indexed", "count", requests.size()));
    }
}
