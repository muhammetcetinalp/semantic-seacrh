package com.example.semantic_search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ColBERT (Token-level Late Interaction) çoklu vektör arama mimarisine ait yapılandırma sınıfı.
 *
 * <p>application.yml dosyasındaki {@code search.colbert} önekini bağlar.
 * ColBERT model servisinin açık olup olmadığını, TEI uç noktasını, model adını ve
 * vektörlerin nerede saklanacağını (Qdrant veya Direct bellek) yönetir.</p>
 */
@Component
@ConfigurationProperties(prefix = "search.colbert")
public class ColbertProperties {

    /** ColBERT arama entegrasyonunun aktif olup olmadığı. */
    private boolean enabled = true;

    /** ColBERT model servisinin (TEI) HTTP uç noktası URL'i. */
    private String endpoint = "http://localhost:8083";

    /** Kullanılan ColBERT modeli adı (örn: jinaai/jina-colbert-v2). */
    private String model = "jinaai/jina-colbert-v2";

    /** Çoklu vektörlerin saklama motoru: "qdrant" veya "direct". */
    private String storage = "qdrant";

    /** @return Saklama motoru adı */
    public String getStorage() {
        return storage;
    }

    /** @param storage Saklama motoru adı */
    public void setStorage(String storage) {
        this.storage = storage;
    }

    /** @return ColBERT aktif mi */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled ColBERT aktiflik durumu */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return ColBERT servis URL'i */
    public String getEndpoint() {
        return endpoint;
    }

    /** @param endpoint ColBERT servis URL'i */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /** @return ColBERT model adı */
    public String getModel() {
        return model;
    }

    /** @param model ColBERT model adı */
    public void setModel(String model) {
        this.model = model;
    }
}
