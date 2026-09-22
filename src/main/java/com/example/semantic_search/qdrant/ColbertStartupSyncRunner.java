package com.example.semantic_search.qdrant;

/**
 * ColBERT başlangıç senkronizasyonu çalıştırıcısı.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.service.ColbertStartupSyncRunner} paketine taşınmıştır.
 */
@Deprecated
public class ColbertStartupSyncRunner extends com.example.semantic_search.service.ColbertStartupSyncRunner {

    public ColbertStartupSyncRunner(com.example.semantic_search.service.ColbertIndexingSyncService syncService) {
        super(syncService);
    }
}
