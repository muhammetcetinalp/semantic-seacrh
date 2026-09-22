package com.example.semantic_search.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Spring Boot uygulaması ayağa kalktığında otomatik olarak devreye giren ve
 * Qdrant çoklu-vektör koleksiyonunun OpenSearch dokümanları ile senkronize olup
 * olmadığını denetleyip eksikleri tamamlayan başlangıç çalıştırıcısı (startup runner).
 */
@Component
public class ColbertStartupSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ColbertStartupSyncRunner.class);

    private final ColbertIndexingSyncService syncService;

    @Value("${search.defaults.default-index-name:olaylar}")
    private String defaultIndexName;

    /**
     * Gerekli senkronizasyon servisini enjekte eden yapıcı metot.
     *
     * @param syncService ColBERT indeks senkronizasyon servisi
     */
    public ColbertStartupSyncRunner(ColbertIndexingSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Uygulama başlatıldığında tetiklenen başlangıç metodu.
     *
     * @param args Komut satırı argümanları
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Uygulama başlangıcında '{}' indeksi için Qdrant ColBERT vektörleri kontrol ediliyor...", defaultIndexName);
            var res = syncService.syncAllFromOpenSearch(defaultIndexName);
            log.info("ColBERT başlangıç senkronizasyonu tamamlandı: {}", res);
        } catch (Exception e) {
            log.warn("ColBERT başlangıç senkronizasyonu ertelendi: {}", e.getMessage());
        }
    }
}
