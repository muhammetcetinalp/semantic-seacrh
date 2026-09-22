package com.example.semantic_search.dto;

import com.example.semantic_search.model.SearchType;

import java.util.List;

/**
 * Arama sorguları sonucunda istemciye döndürülen standart yanıt veri transfer nesnesi (DTO).
 *
 * <p>Bu nesne; arama sonucunda eşleşen dokümanların listesini, toplam bulunan isabet sayısını,
 * sorgunun yürütülme süresini (milisaniye cinsinden) ve kullanılan arama yöntemini barındırır.</p>
 */
public class SearchResponse {

    /** Arama kriterleriyle eşleşen sonuç dokümanlarının listesi. */
    private List<SearchResult> results;

    /** OpenSearch kümesinde sorguyla eşleşen toplam kayıt sayısı. */
    private long totalHits;

    /** Sorgunun veritabanı ve model katmanında tamamlanma süresi (milisaniye). */
    private long tookMs;

    /** Sorgunun çalıştırıldığı yöntem (BM25, SEMANTIC veya HYBRID). */
    private SearchType searchType;

    /**
     * Boş yapıcı metot (Serileştirme kütüphaneleri için gereklidir).
     */
    public SearchResponse() {
    }

    /**
     * Tüm alanları dolduran parametreli yapıcı metot.
     *
     * @param results Eşleşen sonuç dokümanları listesi
     * @param totalHits Toplam bulunan kayıt sayısı
     * @param tookMs İşlemin tamamlanma süresi (ms)
     * @param searchType Kullanılan arama stratejisi
     */
    public SearchResponse(List<SearchResult> results, long totalHits, long tookMs, SearchType searchType) {
        this.results = results;
        this.totalHits = totalHits;
        this.tookMs = tookMs;
        this.searchType = searchType;
    }

    /**
     * Sonuç dokümanları listesini döndürür.
     *
     * @return Eşleşen dökümanlar listesi
     */
    public List<SearchResult> getResults() {
        return results;
    }

    /**
     * Sonuç dokümanları listesini günceller.
     *
     * @param results Yeni döküman listesi
     */
    public void setResults(List<SearchResult> results) {
        this.results = results;
    }

    /**
     * Eşleşen toplam isabet sayısını döndürür.
     *
     * @return Toplam isabet sayısı
     */
    public long getTotalHits() {
        return totalHits;
    }

    /**
     * Eşleşen toplam isabet sayısını ayarlar.
     *
     * @param totalHits Toplam isabet sayısı
     */
    public void setTotalHits(long totalHits) {
        this.totalHits = totalHits;
    }

    /**
     * Sorgunun icra edilme süresini (ms) döndürür.
     *
     * @return Milisaniye cinsinden geçen süre
     */
    public long getTookMs() {
        return tookMs;
    }

    /**
     * Sorgu icra süresini günceller.
     *
     * @param tookMs Milisaniye cinsinden geçen süre
     */
    public void setTookMs(long tookMs) {
        this.tookMs = tookMs;
    }

    /**
     * Kullanılan arama yöntemini döndürür.
     *
     * @return Arama türü enum değeri
     */
    public SearchType getSearchType() {
        return searchType;
    }

    /**
     * Kullanılan arama yöntemini ayarlar.
     *
     * @param searchType Arama türü enum değeri
     */
    public void setSearchType(SearchType searchType) {
        this.searchType = searchType;
    }
}
