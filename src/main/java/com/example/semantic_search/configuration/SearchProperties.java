package com.example.semantic_search.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search.defaults")
public class SearchProperties {

    private int limit = 20;
    private int maxLimit = 100;
    private String defaultSearchType = "HYBRID";
    private String defaultIndexName = "entities";

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(int maxLimit) {
        this.maxLimit = maxLimit;
    }

    public String getDefaultSearchType() {
        return defaultSearchType;
    }

    public void setDefaultSearchType(String defaultSearchType) {
        this.defaultSearchType = defaultSearchType;
    }

    public String getDefaultIndexName() {
        return defaultIndexName;
    }

    public void setDefaultIndexName(String defaultIndexName) {
        this.defaultIndexName = defaultIndexName;
    }
}
