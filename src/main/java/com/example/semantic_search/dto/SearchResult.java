package com.example.semantic_search.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Arama sorguları sonucunda tek bir dokümanın arayüze/istemciye aktarılan temsil DTO'su.
 *
 * <p>Bu model; dokümanın kimliğini, başlığını, içerik metinlerini (kısa özet ve detaylı rapor),
 * taktik/operasyonel alanlarını (birim, adres, tarih, koordinat), arama alakası puanını (score)
 * ve zaman damgalarını barındırır.</p>
 */
public class SearchResult {

    /** Dokümanın benzersiz kimliği (ID). */
    private String id;

    /** Doküman sınıflandırma türü (örn: OLAY, DEVRİYE, İHBAR). */
    private String type;

    /** Doküman başlığı. */
    private String title;

    /** İndekslenen birleşik arama metni. */
    private String searchText;

    /** Kısa olay özeti. */
    private String shortText;

    /** Detaylı olay raporu metni. */
    private String longText;

    /** Görevli askeri veya emniyet biriminin adı. */
    private String birim;

    /** Olayın gerçekleştiği adres / yer tanımı. */
    private String adres;

    /** Olayın meydana geldiği tarih ve saat (ISO-8601 veya metin formatında). */
    private String tarih;

    /** Coğrafi konum (Enlem/Boylam nesnesi veya metin). */
    private Object konum;

    /** Dokümana ait yapısal filtre alanları haritası. */
    private Map<String, Object> structuredFields;

    /** Dokümana ait ek meta veriler haritası. */
    private Map<String, Object> metadata;

    /** Arama algoritması (BM25 veya Kosinüs benzerliği) tarafından hesaplanan alaka puanı. */
    private double score;

    /** Dokümanın ilk oluşturulma zaman damgası. */
    private Instant createdAt;

    /** Dokümanın son güncellenme zaman damgası. */
    private Instant updatedAt;

    /**
     * Boş yapıcı metot.
     */
    public SearchResult() {
    }

    /**
     * Doküman kimliğini döndürür.
     *
     * @return Doküman ID
     */
    public String getId() {
        return id;
    }

    /**
     * Doküman kimliğini ayarlar.
     *
     * @param id Doküman ID
     */
    public void setId(String id) {
        this.id = id;
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
     * Doküman türünü ayarlar.
     *
     * @param type Doküman türü
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Doküman başlığını döndürür.
     *
     * @return Başlık metni
     */
    public String getTitle() {
        return title;
    }

    /**
     * Doküman başlığını ayarlar.
     *
     * @param title Başlık metni
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Birleşik arama metnini döndürür.
     *
     * @return Arama metni
     */
    public String getSearchText() {
        return searchText;
    }

    /**
     * Birleşik arama metnini ayarlar.
     *
     * @param searchText Arama metni
     */
    public void setSearchText(String searchText) {
        this.searchText = searchText;
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
     * Ek meta veri haritasını döndürür.
     *
     * @return Meta veriler
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Ek meta veri haritasını ayarlar.
     *
     * @param metadata Meta veriler
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Dokümanın alaka puanını döndürür.
     *
     * @return Alaka skoru
     */
    public double getScore() {
        return score;
    }

    /**
     * Dokümanın alaka puanını ayarlar.
     *
     * @param score Alaka skoru
     */
    public void setScore(double score) {
        this.score = score;
    }

    /**
     * Oluşturulma anını döndürür.
     *
     * @return Oluşturulma zamanı
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Oluşturulma anını ayarlar.
     *
     * @param createdAt Oluşturulma zamanı
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Son güncellenme anını döndürür.
     *
     * @return Son güncellenme zamanı
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Son güncellenme anını ayarlar.
     *
     * @param updatedAt Son güncellenme zamanı
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
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
     * Detaylı olay raporunu döndürür.
     *
     * @return Ayrıntılı rapor metni
     */
    public String getLongText() {
        return longText;
    }

    /**
     * Detaylı olay raporunu ayarlar.
     *
     * @param longText Ayrıntılı rapor metni
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
     * Olay adresi / konum metnini döndürür.
     *
     * @return Adres metni
     */
    public String getAdres() {
        return adres;
    }

    /**
     * Olay adresi / konum metnini ayarlar.
     *
     * @param adres Adres metni
     */
    public void setAdres(String adres) {
        this.adres = adres;
    }

    /**
     * Olay tarihini döndürür.
     *
     * @return Tarih metni
     */
    public String getTarih() {
        return tarih;
    }

    /**
     * Olay tarihini ayarlar.
     *
     * @param tarih Tarih metni
     */
    public void setTarih(String tarih) {
        this.tarih = tarih;
    }

    /**
     * Coğrafi koordinat verisini döndürür.
     *
     * @return Konum nesnesi
     */
    public Object getKonum() {
        return konum;
    }

    /**
     * Coğrafi koordinat verisini ayarlar.
     *
     * @param konum Konum nesnesi
     */
    public void setKonum(Object konum) {
        this.konum = konum;
    }
}
