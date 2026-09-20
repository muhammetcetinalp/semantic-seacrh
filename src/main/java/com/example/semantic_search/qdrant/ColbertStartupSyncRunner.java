package com.example.semantic_search.qdrant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Automatically ensures Qdrant multi-vector collection is synchronized
 * with OpenSearch documents upon application startup.
 */
@Component
public class ColbertStartupSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ColbertStartupSyncRunner.class);

    private final ColbertIndexingSyncService syncService;

    @org.springframework.beans.factory.annotation.Value("${search.defaults.default-index-name:olaylar}")
    private String defaultIndexName;

    public ColbertStartupSyncRunner(ColbertIndexingSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Checking and syncing ColBERT vectors to Qdrant for index '{}' on startup...", defaultIndexName);
            var res = syncService.syncAllFromOpenSearch(defaultIndexName);
            log.info("ColBERT startup sync finished: {}", res);
        } catch (Exception e) {
            log.warn("ColBERT startup sync deferred: {}", e.getMessage());
        }
    }
}
