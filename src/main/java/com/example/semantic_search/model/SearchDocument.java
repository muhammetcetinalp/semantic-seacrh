package com.example.semantic_search.model;

import java.time.Instant;
import java.util.Map;

/**
 * OpenSearch arama motorunda ve dahili hafızada temsil edilen birleşik arama dokümanı modeli.
 *
 * <p>Harici veri kaynaklarından (örn. Olaylar JSON, Kafka olayları, REST istekleri) gelen veriler
 * bu ortak modele dönüştürülür. Yapısal filtre alanları (durum, bölge, birim, vb.) ile anlamsal
 * metin alanları (searchText, shortText, longText) birbirinden ayrı tutularak hem hızlı filtreleme
 * hem de yüksek doğruluklu vektör gömmesi (embedding) üretimi sağlanır.</p>
 */
public class SearchDocument {

    /** Dokümanın benzersiz kimliği (ID). */
    private String id;

    /** Dokümanın ait olduğu OpenSearch indeks adı. */
    private String indexName;

    /** Dokümanın ana türü / kategorisi (örn: ASKERI_OLAY, DEVRİYE). */
    private String type;

    /** Doküman başlığı. */
    private String title;

    /** Vektör gömmesi üretmek ve tam metin aramak için birleştirilmiş ana metin. */
    private String searchText;

    /** Dokümana ait kısa özet metni. */
    private String shortText;

    /** Dokümanın tüm detaylarını içeren uzun rapor metni. */
    private String longText;

    /** Görevli askeri veya emniyet birimi. */
    private String birim;

    /** Olay yeri veya adres bilgisi. */
    private String adres;

    /** Olay zamanı / tarihi. */
    private String tarih;

    /** Coğrafi konum koordinatları (Geo-point). */
    private Object konum;

    /** Dinamik filtreleme için yapısal alanlar haritası. */
    private Map<String, Object> structuredFields;

    /** İlave meta veri haritası. */
    private Map<String, Object> metadata;

    /** BGE-M3 veya ilgili modelden üretilen 1024 boyutlu yoğun vektör gömmesi. */
    private float[] embedding;

    /** Dokümanın oluşturulma zaman damgası. */
    private Instant createdAt;

    /** Dokümanın son güncellenme zaman damgası. */
    private Instant updatedAt;

    /**
     * Boş yapıcı metot.
     */
    public SearchDocument() {
    }

    /**
     * Builder nesnesi üzerinden SearchDocument oluşturan yapıcı metot.
     *
     * @param builder Değerleri barındıran Builder örneği
     */
    public SearchDocument(Builder builder) {
        this.id = builder.id;
        this.indexName = builder.indexName;
        this.type = builder.type;
        this.title = builder.title;
        this.searchText = builder.searchText;
        this.shortText = builder.shortText;
        this.longText = builder.longText;
        this.birim = builder.birim;
        this.adres = builder.adres;
        this.tarih = builder.tarih;
        this.konum = builder.konum;
        this.structuredFields = builder.structuredFields;
        this.metadata = builder.metadata;
        this.embedding = builder.embedding;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    /**
     * Yeni bir Builder örneği oluşturur.
     *
     * @return Builder nesnesi
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * SearchDocument nesnelerini akıcı (fluent) arayüz ile inşa eden Builder sınıfı.
     */
    public static class Builder {
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
        private Map<String, Object> structuredFields;
        private Map<String, Object> metadata;
        private float[] embedding;
        private Instant createdAt;
        private Instant updatedAt;

        /**
         * Doküman kimliğini ayarlar.
         *
         * @param id Doküman ID
         * @return Builder örneği
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * İndeks adını ayarlar.
         *
         * @param indexName İndeks adı
         * @return Builder örneği
         */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        /**
         * Doküman türünü belirler.
         *
         * @param type Doküman türü
         * @return Builder örneği
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Başlık bilgisini ayarlar.
         *
         * @param title Başlık
         * @return Builder örneği
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Arama metnini ayarlar.
         *
         * @param searchText Arama metni
         * @return Builder örneği
         */
        public Builder searchText(String searchText) {
            this.searchText = searchText;
            return this;
        }

        /**
         * Kısa metin özetini ayarlar.
         *
         * @param shortText Kısa metin
         * @return Builder örneği
         */
        public Builder shortText(String shortText) {
            this.shortText = shortText;
            return this;
        }

        /**
         * Detaylı rapor metnini ayarlar.
         *
         * @param longText Detaylı rapor
         * @return Builder örneği
         */
        public Builder longText(String longText) {
            this.longText = longText;
            return this;
        }

        /**
         * Görevli birim adını ayarlar.
         *
         * @param birim Birim adı
         * @return Builder örneği
         */
        public Builder birim(String birim) {
            this.birim = birim;
            return this;
        }

        /**
         * Adres bilgisini ayarlar.
         *
         * @param adres Adres metni
         * @return Builder örneği
         */
        public Builder adres(String adres) {
            this.adres = adres;
            return this;
        }

        /**
         * Olay tarihini ayarlar.
         *
         * @param tarih Tarih metni
         * @return Builder örneği
         */
        public Builder tarih(String tarih) {
            this.tarih = tarih;
            return this;
        }

        /**
         * Coğrafi konumu ayarlar.
         *
         * @param konum Konum nesnesi
         * @return Builder örneği
         */
        public Builder konum(Object konum) {
            this.konum = konum;
            return this;
        }

        /**
         * Yapısal alanlar haritasını ayarlar.
         *
         * @param structuredFields Yapısal alanlar
         * @return Builder örneği
         */
        public Builder structuredFields(Map<String, Object> structuredFields) {
            this.structuredFields = structuredFields;
            return this;
        }

        /**
         * Meta veriler haritasını ayarlar.
         *
         * @param metadata Meta veriler
         * @return Builder örneği
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Yoğun vektör gömmesini ayarlar.
         *
         * @param embedding Float vektör dizisi
         * @return Builder örneği
         */
        public Builder embedding(float[] embedding) {
            this.embedding = embedding;
            return this;
        }

        /**
         * Oluşturulma anını ayarlar.
         *
         * @param createdAt Oluşturulma zamanı
         * @return Builder örneği
         */
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Güncellenme anını ayarlar.
         *
         * @param updatedAt Güncellenme zamanı
         * @return Builder örneği
         */
        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * Yapılandırılan değerlerle SearchDocument nesnesi üretir.
         *
         * @return Oluşturulan SearchDocument nesnesi
         */
        public SearchDocument build() {
            return new SearchDocument(this);
        }
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
     * İndeks adını döndürür.
     *
     * @return İndeks adı
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * İndeks adını ayarlar.
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
     * Başlık bilgisini döndürür.
     *
     * @return Başlık
     */
    public String getTitle() {
        return title;
    }

    /**
     * Başlık bilgisini ayarlar.
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
     * Kısa metin özetini döndürür.
     *
     * @return Kısa metin
     */
    public String getShortText() {
        return shortText;
    }

    /**
     * Kısa metin özetini ayarlar.
     *
     * @param shortText Kısa metin
     */
    public void setShortText(String shortText) {
        this.shortText = shortText;
    }

    /**
     * Detaylı rapor metnini döndürür.
     *
     * @return Detaylı rapor
     */
    public String getLongText() {
        return longText;
    }

    /**
     * Detaylı rapor metnini ayarlar.
     *
     * @param longText Detaylı rapor
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
     * Adres bilgisini döndürür.
     *
     * @return Adres metni
     */
    public String getAdres() {
        return adres;
    }

    /**
     * Adres bilgisini ayarlar.
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
     * Coğrafi konumu döndürür.
     *
     * @return Konum nesnesi
     */
    public Object getKonum() {
        return konum;
    }

    /**
     * Coğrafi konumu ayarlar.
     *
     * @param konum Konum nesnesi
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
     * Meta veriler haritasını döndürür.
     *
     * @return Meta veriler
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Meta veriler haritasını ayarlar.
     *
     * @param metadata Meta veriler
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Yoğun vektör gömmesini (embedding) döndürür.
     *
     * @return Float vektör dizisi
     */
    public float[] getEmbedding() {
        return embedding;
    }

    /**
     * Yoğun vektör gömmesini ayarlar.
     *
     * @param embedding Float vektör dizisi
     */
    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
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
     * Güncellenme anını döndürür.
     *
     * @return Güncellenme zamanı
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Güncellenme anını ayarlar.
     *
     * @param updatedAt Güncellenme zamanı
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
