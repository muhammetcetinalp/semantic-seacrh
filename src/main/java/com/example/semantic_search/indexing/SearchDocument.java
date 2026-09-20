package com.example.semantic_search.indexing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified search document model. Domain objects are mapped to this model
 * before being indexed into OpenSearch.
 *
 * <p>Structured fields (status, region, priority, etc.) are kept separate
 * from semantic content (searchText) to enable independent filtering and
 * embedding generation.</p>
 */
public class SearchDocument {

    private String id;
    private String indexName;
    private String type;
    private String title;
    private String searchText;
    private String shortText;
    private String longText;
    private String birim;
    private String adres;
    private String tarih;
    private Object konum;
    private List<String> tags;
    private Map<String, Object> structuredFields;
    private Map<String, Object> metadata;
    private float[] embedding;
    private Instant createdAt;
    private Instant updatedAt;

    public SearchDocument() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Map<String, Object> getStructuredFields() {
        return structuredFields;
    }

    public void setStructuredFields(Map<String, Object> structuredFields) {
        this.structuredFields = structuredFields;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getShortText() { return shortText; }
    public void setShortText(String shortText) { this.shortText = shortText; }

    public String getLongText() { return longText; }
    public void setLongText(String longText) { this.longText = longText; }

    public String getBirim() { return birim; }
    public void setBirim(String birim) { this.birim = birim; }

    public String getAdres() { return adres; }
    public void setAdres(String adres) { this.adres = adres; }

    public String getTarih() { return tarih; }
    public void setTarih(String tarih) { this.tarih = tarih; }

    public Object getKonum() { return konum; }
    public void setKonum(Object konum) { this.konum = konum; }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SearchDocument doc = new SearchDocument();

        public Builder id(String id) { doc.id = id; return this; }
        public Builder indexName(String indexName) { doc.indexName = indexName; return this; }
        public Builder type(String type) { doc.type = type; return this; }
        public Builder title(String title) { doc.title = title; return this; }
        public Builder searchText(String searchText) { doc.searchText = searchText; return this; }
        public Builder shortText(String shortText) { doc.shortText = shortText; return this; }
        public Builder longText(String longText) { doc.longText = longText; return this; }
        public Builder birim(String birim) { doc.birim = birim; return this; }
        public Builder adres(String adres) { doc.adres = adres; return this; }
        public Builder tarih(String tarih) { doc.tarih = tarih; return this; }
        public Builder konum(Object konum) { doc.konum = konum; return this; }
        public Builder tags(List<String> tags) { doc.tags = tags != null ? new ArrayList<>(tags) : null; return this; }
        public Builder structuredFields(Map<String, Object> fields) { doc.structuredFields = fields != null ? new HashMap<>(fields) : null; return this; }
        public Builder metadata(Map<String, Object> metadata) { doc.metadata = metadata != null ? new HashMap<>(metadata) : null; return this; }
        public Builder embedding(float[] embedding) { doc.embedding = embedding; return this; }
        public Builder createdAt(Instant createdAt) { doc.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { doc.updatedAt = updatedAt; return this; }

        public SearchDocument build() {
            return doc;
        }
    }
}
