package com.example.semantic_search.indexing;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * API request model for indexing a document.
 * The service converts this to a {@link SearchDocument}, generates the
 * embedding from {@code searchText}, and indexes into OpenSearch.
 */
public class IndexDocumentRequest {

    @NotBlank(message = "Document id must not be blank")
    private String id;

    private String indexName;

    @NotBlank(message = "Document type must not be blank")
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

    public IndexDocumentRequest() {
    }

    public String getShortText() {
        return shortText;
    }

    public void setShortText(String shortText) {
        this.shortText = shortText;
    }

    public String getLongText() {
        return longText;
    }

    public void setLongText(String longText) {
        this.longText = longText;
    }

    public String getBirim() {
        return birim;
    }

    public void setBirim(String birim) {
        this.birim = birim;
    }

    public String getAdres() {
        return adres;
    }

    public void setAdres(String adres) {
        this.adres = adres;
    }

    public String getTarih() {
        return tarih;
    }

    public void setTarih(String tarih) {
        this.tarih = tarih;
    }

    public Object getKonum() {
        return konum;
    }

    public void setKonum(Object konum) {
        this.konum = konum;
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
}
