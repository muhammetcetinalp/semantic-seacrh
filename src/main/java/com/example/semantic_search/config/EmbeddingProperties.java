package com.example.semantic_search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Yoğun vektör gömme (Dense Embedding - BGE-M3) üretici servisine ait yapılandırma sınıfı.
 *
 * <p>application.yml dosyasındaki {@code search.embedding} önekini bağlar.
 * Sağlayıcı türü (mock, tei, rest), API uç noktası, model adı ve vektör boyutu (1024) gibi
 * temel yapay zeka parametrelerini yönetir.</p>
 */
@ConfigurationProperties(prefix = "search.embedding")
public class EmbeddingProperties {

    /** Vektör sağlayıcı türü: "mock" (test için), "tei" (HuggingFace TEI konteyneri), "rest". */
    private String provider = "mock";

    /** Vektörleme servisinin REST uç noktası URL adresi. */
    private String endpoint = "http://localhost:8081";

    /** Kullanılan HuggingFace model adı (örn: BAAI/bge-m3). */
    private String model = "BAAI/bge-m3";

    /** Üretilen vektörün boyutu (BGE-M3 için 1024 boyutludur). */
    private int dimensions = 1024;

    /** Model çağrıları için HTTP zaman aşımı süresi (milisaniye). */
    private int timeout = 30000;

    /** Servis ulaşılamazsa sahte (mock) deterministik vektör üretilip üretilmeyeceği. */
    private boolean mockEnabled = true;

    /** Model sunucusu için API anahtarı (varsa Authorization Bearer veya X-API-Key başlığında gönderilir). */
    private String apiKey;

    /** API anahtarının gönderileceği HTTP başlığı (varsayılan: Authorization). */
    private String apiKeyHeader = "Authorization";

    /** @return API anahtarı */
    public String getApiKey() {
        return apiKey;
    }

    /** @param apiKey API anahtarı */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /** @return API anahtarı başlığı */
    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    /** @param apiKeyHeader API anahtarı başlığı */
    public void setApiKeyHeader(String apiKeyHeader) {
        this.apiKeyHeader = apiKeyHeader;
    }

    /** @return Sağlayıcı türü */
    public String getProvider() {
        return provider;
    }

    /** @param provider Sağlayıcı türü */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /** @return Servis uç noktası URL'i */
    public String getEndpoint() {
        return endpoint;
    }

    /** @param endpoint Servis uç noktası URL'i */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /** @return Model adı */
    public String getModel() {
        return model;
    }

    /** @param model Model adı */
    public void setModel(String model) {
        this.model = model;
    }

    /** @return Vektör boyutu */
    public int getDimensions() {
        return dimensions;
    }

    /** @param dimensions Vektör boyutu */
    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    /** @return Zaman aşımı süresi (ms) */
    public int getTimeout() {
        return timeout;
    }

    /** @param timeout Zaman aşımı süresi (ms) */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /** @return Mock sağlayıcı aktif mi */
    public boolean isMockEnabled() {
        return mockEnabled;
    }

    /** @param mockEnabled Mock sağlayıcı aktiflik durumu */
    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }
}
