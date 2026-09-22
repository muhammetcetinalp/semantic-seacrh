package com.example.semantic_search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Qdrant vektör veritabanına bağlantı ve koleksiyon ayarlarını yöneten yapılandırma sınıfı.
 *
 * <p>application.yml dosyasındaki {@code search.qdrant} önekini bağlar.
 * ColBERT token vektörlerinin saklandığı koleksiyon adı, vektör boyutu (128) ve REST bağlantı
 * zaman aşımı sürelerini kontrol eder.</p>
 */
@Component
@ConfigurationProperties(prefix = "search.qdrant")
public class QdrantProperties {

    /** Qdrant entegrasyonunun aktif olup olmadığı. */
    private boolean enabled = true;

    /** Qdrant sunucusu HTTP REST API uç noktası URL'i. */
    private String endpoint = "http://localhost:6333";

    /** ColBERT çoklu vektörlerinin yazıldığı koleksiyon adı. */
    private String collectionName = "colbert_entities";

    /** Her bir token vektörünün boyutu (ColBERT v2 için 128 boyutludur). */
    private int vectorSize = 128;

    /** Qdrant API istekleri için zaman aşımı süresi (ms). */
    private int timeoutMs = 10000;

    /** @return Qdrant aktif mi */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled Qdrant aktiflik durumu */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return Qdrant sunucu URL'i */
    public String getEndpoint() {
        return endpoint;
    }

    /** @param endpoint Qdrant sunucu URL'i */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /** @return Koleksiyon adı */
    public String getCollectionName() {
        return collectionName;
    }

    /** @param collectionName Koleksiyon adı */
    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    /** @return Vektör boyutu */
    public int getVectorSize() {
        return vectorSize;
    }

    /** @param vectorSize Vektör boyutu */
    public void setVectorSize(int vectorSize) {
        this.vectorSize = vectorSize;
    }

    /** @return Zaman aşımı süresi (ms) */
    public int getTimeoutMs() {
        return timeoutMs;
    }

    /** @param timeoutMs Zaman aşımı süresi (ms) */
    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
