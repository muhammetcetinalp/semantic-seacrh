package com.example.semantic_search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Arama servisi için genel varsayılan değerleri belirleyen yapılandırma sınıfı.
 *
 * <p>application.yml dosyasındaki {@code search.defaults} önekini bağlar.
 * Varsayılan sonuç limiti (20), maksimum sonuç tavanı (100), varsayılan indeks adı ("entities")
 * ve varsayılan arama modunu ("HYBRID") belirler.</p>
 */
@ConfigurationProperties(prefix = "search.defaults")
public class SearchProperties {

    /** İstemci limit belirtmediğinde dönülecek varsayılan sonuç adedi. */
    private int limit = 20;

    /** İstemcinin isteyebileceği en fazla sonuç sayısı tavanı. */
    private int maxLimit = 100;

    /** Varsayılan arama stratejisi (örn: HYBRID). */
    private String defaultSearchType = "HYBRID";

    /** Hedef indeks belirtilmediğinde sorgulanacak varsayılan indeks. */
    private String defaultIndexName = "entities";

    /** @return Varsayılan sonuç adedi */
    public int getLimit() {
        return limit;
    }

    /** @param limit Varsayılan sonuç adedi */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /** @return Maksimum sonuç tavanı */
    public int getMaxLimit() {
        return maxLimit;
    }

    /** @param maxLimit Maksimum sonuç tavanı */
    public void setMaxLimit(int maxLimit) {
        this.maxLimit = maxLimit;
    }

    /** @return Varsayılan arama türü */
    public String getDefaultSearchType() {
        return defaultSearchType;
    }

    /** @param defaultSearchType Varsayılan arama türü */
    public void setDefaultSearchType(String defaultSearchType) {
        this.defaultSearchType = defaultSearchType;
    }

    /** @return Varsayılan indeks adı */
    public String getDefaultIndexName() {
        return defaultIndexName;
    }

    /** @param defaultIndexName Varsayılan indeks adı */
    public void setDefaultIndexName(String defaultIndexName) {
        this.defaultIndexName = defaultIndexName;
    }
}
