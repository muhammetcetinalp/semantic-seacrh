package com.example.semantic_search.dto;

import com.example.semantic_search.model.SearchType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * Kullanıcı veya istemci tarafından gönderilen standart arama isteği veri transfer nesnesi (DTO).
 *
 * <p>Bu sınıf, OpenSearch sorgu DSL detaylarını dış dünyadan soyutlar. İstemciler sadece
 * arama metnini, filtreleri, doküman tiplerini ve sayfalama parametrelerini belirterek arama yapabilir.</p>
 *
 * <ul>
 *   <li><b>query:</b> Kullanıcının aratmak istediği doğal dil veya sözcüksel sorgu metni.</li>
 *   <li><b>types:</b> Filtrelenecek doküman türleri listesi (örn: OLAY, DEVRİYE, İHBAR).</li>
 *   <li><b>filters:</b> Yapısal alanlar üzerine uygulanacak anahtar-değer filtre haritası.</li>
 *   <li><b>searchType:</b> Arama stratejisi türü (BM25, SEMANTIC veya HYBRID).</li>
 *   <li><b>limit & offset:</b> Sayfalama kısıtları.</li>
 *   <li><b>indexName:</b> Hedef OpenSearch indeks adı (boş bırakılırsa varsayılan indeks kullanılır).</li>
 * </ul>
 */
public class SearchRequest {

    /** Aranacak anahtar kelime veya doğal dil sorgu cümlesi (Boş bırakılamaz). */
    @NotBlank(message = "Arama sorgusu boş olamaz")
    private String query;

    /** Filtrelenmek istenen doküman tipleri listesi. */
    private List<String> types;

    /** Dinamik filtreleme parametreleri haritası (örn: status -> active). */
    private Map<String, Object> filters;

    /** Uygulanacak arama yöntemi: BM25, SEMANTIC veya HYBRID. */
    private SearchType searchType;

    /** Döndürülecek maksimum sonuç sayısı (1 ile 100 arasında olmalıdır). */
    @Min(value = 1, message = "Sonuç limiti en az 1 olmalıdır")
    @Max(value = 100, message = "Sonuç limiti en fazla 100 olabilir")
    private Integer limit;

    /** Sayfalama için başlangıç ofseti (0 veya daha büyük olmalıdır). */
    @Min(value = 0, message = "Sayfalama ofseti negatif olamaz")
    private Integer offset;

    /** Sorgunun yönlendirileceği özel indeks adı. */
    private String indexName;

    /**
     * Varsayılan yapıcı metot (JSON serileştirme/deserileştirme için gereklidir).
     */
    public SearchRequest() {
    }

    /**
     * Arama sorgusu metnini döndürür.
     *
     * @return Kullanıcının girdiği arama sorgusu
     */
    public String getQuery() {
        return query;
    }

    /**
     * Arama sorgusu metnini günceller.
     *
     * @param query Kullanıcı sorgu metni
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * Filtrelenen doküman tipleri listesini döndürür.
     *
     * @return Doküman tipleri listesi veya null
     */
    public List<String> getTypes() {
        return types;
    }

    /**
     * Filtrelenecek doküman tiplerini belirler.
     *
     * @param types Doküman tipleri listesi
     */
    public void setTypes(List<String> types) {
        this.types = types;
    }

    /**
     * Yapısal filtreleme kriterlerini döndürür.
     *
     * @return Filtre anahtar-değer haritası
     */
    public Map<String, Object> getFilters() {
        return filters;
    }

    /**
     * Yapısal filtreleme kriterlerini günceller.
     *
     * @param filters Filtre anahtar-değer haritası
     */
    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }

    /**
     * Seçili arama yöntemini döndürür.
     *
     * @return BM25, SEMANTIC veya HYBRID arama türü
     */
    public SearchType getSearchType() {
        return searchType;
    }

    /**
     * Arama yöntemini günceller.
     *
     * @param searchType Arama stratejisi
     */
    public void setSearchType(SearchType searchType) {
        this.searchType = searchType;
    }

    /**
     * İstenen sonuç adedini döndürür.
     *
     * @return Maksimum sonuç sayısı
     */
    public Integer getLimit() {
        return limit;
    }

    /**
     * İstenen sonuç adedini belirler.
     *
     * @param limit Sonuç adedi
     */
    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    /**
     * Sayfalama ofsetini döndürür.
     *
     * @return Başlangıç ofset değeri
     */
    public Integer getOffset() {
        return offset;
    }

    /**
     * Sayfalama ofsetini belirler.
     *
     * @param offset Başlangıç ofset değeri
     */
    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    /**
     * Hedeflenen indeks adını döndürür.
     *
     * @return İndeks adı veya null (varsayılan indeks kullanılır)
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * Hedeflenen indeks adını belirler.
     *
     * @param indexName İndeks adı
     */
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }
}
