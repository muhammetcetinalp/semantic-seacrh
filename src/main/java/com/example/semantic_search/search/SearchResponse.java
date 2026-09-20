package com.example.semantic_search.search;

import java.util.List;

public class SearchResponse {

    private List<SearchResult> results;
    private long totalHits;
    private long tookMs;
    private SearchType searchType;

    public SearchResponse() {
    }

    public SearchResponse(List<SearchResult> results, long totalHits, long tookMs, SearchType searchType) {
        this.results = results;
        this.totalHits = totalHits;
        this.tookMs = tookMs;
        this.searchType = searchType;
    }

    public List<SearchResult> getResults() {
        return results;
    }

    public void setResults(List<SearchResult> results) {
        this.results = results;
    }

    public long getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(long totalHits) {
        this.totalHits = totalHits;
    }

    public long getTookMs() {
        return tookMs;
    }

    public void setTookMs(long tookMs) {
        this.tookMs = tookMs;
    }

    public SearchType getSearchType() {
        return searchType;
    }

    public void setSearchType(SearchType searchType) {
        this.searchType = searchType;
    }
}
