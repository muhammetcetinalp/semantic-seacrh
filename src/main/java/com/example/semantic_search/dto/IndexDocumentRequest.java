package com.example.semantic_search.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * OpenSearch indeksine yeni bir doküman eklemek veya mevcut olanı güncellemek için kullanılan istek DTO'su.
 *
 * <p>Servis katmanı (IndexingService) bu nesneyi bir {@code SearchDocument} modeline dönüştürür,
 * {@code searchText} içeriğinden yapay zeka modelini kullanarak yoğun vektör gömmesini (embedding)
 * oluşturur ve dokümanı OpenSearch ile varsa Qdrant veritabanına kaydeder.</p>
 */
public class IndexDocumentRequest {

    /** Dokümanın benzersiz kimliği (ID) - Zorunlu alandır. */
    @NotBlank(message = "Doküman ID alanı boş bırakılamaz")
    private String id;

    /** Hedef OpenSearch indeks adı (Belirtilmezse varsayılan indeks kullanılır). */
    private String indexName;

    /** Dokümanın türü (örn: OLAY, DEVRİYE, İHBAR) - Zorunlu alandır. */
    @NotBlank(message = "Doküman türü (type) boş bırakılamaz")
    private String type;

    /** Dokümanın başlığı. */
    private String title;

    /** İndekslenecek ve vektörleştirilecek birleşik arama metni. */
    private String searchText;

    /** Kısa olay özeti. */
    private String shortText;

    /** Ayrıntılı olay raporu metni. */
    private String longText;

    /** Görevli askeri veya güvenlik birimi adı. */
    private String birim;

    /** Olayın gerçekleştiği adres / yer bilgisi. */
    private String adres;

    /** Olayın gerçekleştiği tarih ve saat. */
    private String tarih;

    /** Coğrafi konum (enlem/boylam). */
    private Object konum;

    /** Filtreleme ve analiz amacıyla kullanılacak ek yapısal alanlar. */
    private Map<String, Object> structuredFields;

    /** Ek serbest meta veriler. */
    private Map<String, Object> metadata;

    /**
     * Varsayılan yapıcı metot.
     */
    public IndexDocumentRequest() {
    }

    /**
     * Doküman ID değerini döndürür.
     *
     * @return Doküman ID
     */
    public String getId() {
        return id;
    }

    /**
     * Doküman ID değerini ayarlar.
     *
     * @param id Doküman ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Hedef indeks adını döndürür.
     *
     * @return İndeks adı
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * Hedef indeks adını belirler.
     *
     * @param indexName İndeks adı
     */
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    /**
     * Doküman türünü döndürür.
     *
     * @return Doküman türü
     */
    public String getType() {
        return type;
    }

    /**
     * Doküman türünü belirler.
     *
     * @param type Doküman türü
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Doküman başlığını döndürür.
     *
     * @return Başlık
     */
    public String getTitle() {
        return title;
    }

    /**
     * Doküman başlığını ayarlar.
     *
     * @param title Başlık
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Arama metnini döndürür.
     *
     * @return Arama metni
     */
    public String getSearchText() {
        return searchText;
    }

    /**
     * Arama metnini ayarlar.
     *
     * @param searchText Arama metni
     */
    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    /**
     * Kısa olay özetini döndürür.
     *
     * @return Kısa özet metni
     */
    public String getShortText() {
        return shortText;
    }

    /**
     * Kısa olay özetini ayarlar.
     *
     * @param shortText Kısa özet metni
     */
    public void setShortText(String shortText) {
        this.shortText = shortText;
    }

    /**
     * Ayrıntılı olay raporunu döndürür.
     *
     * @return Detaylı rapor metni
     */
    public String getLongText() {
        return longText;
    }

    /**
     * Ayrıntılı olay raporunu ayarlar.
     *
     * @param longText Detaylı rapor metni
     */
    public void setLongText(String longText) {
        this.longText = longText;
    }

    /**
     * Görevli birim adını döndürür.
     *
     * @return Birim adı
     */
    public String getBirim() {
        return birim;
    }

    /**
     * Görevli birim adını ayarlar.
     *
     * @param birim Birim adı
     */
    public void setBirim(String birim) {
        this.birim = birim;
    }

    /**
     * Olay yeri / adres bilgisini döndürür.
     *
     * @return Adres metni
     */
    public String getAdres() {
        return adres;
    }

    /**
     * Olay yeri / adres bilgisini ayarlar.
     *
     * @param adres Adres metni
     */
    public void setAdres(String adres) {
        this.adres = adres;
    }

    /**
     * Olay tarihini döndürür.
     *
     * @return Olay tarihi
     */
    public String getTarih() {
        return tarih;
    }

    /**
     * Olay tarihini ayarlar.
     *
     * @param tarih Olay tarihi
     */
    public void setTarih(String tarih) {
        this.tarih = tarih;
    }

    /**
     * Coğrafi koordinat nesnesini döndürür.
     *
     * @return Konum verisi
     */
    public Object getKonum() {
        return konum;
    }

    /**
     * Coğrafi koordinat nesnesini ayarlar.
     *
     * @param konum Konum verisi
     */
    public void setKonum(Object konum) {
        this.konum = konum;
    }

    /**
     * Yapısal alanlar haritasını döndürür.
     *
     * @return Yapısal alanlar
     */
    public Map<String, Object> getStructuredFields() {
        return structuredFields;
    }

    /**
     * Yapısal alanlar haritasını ayarlar.
     *
     * @param structuredFields Yapısal alanlar
     */
    public void setStructuredFields(Map<String, Object> structuredFields) {
        this.structuredFields = structuredFields;
    }

    /**
     * Ek meta veriler haritasını döndürür.
     *
     * @return Meta veriler
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Ek meta veriler haritasını ayarlar.
     *
     * @param metadata Meta veriler
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
