# 🧭 Yeni Veri Modeli Uçtan Uca Entegrasyon ve Uyarlama Rehberi

Bu kılavuz; projedeki mevcut 10.000 adetlik demo "Olaylar" verisi yerine **airgap (kapalı devre) ortamdaki gerçek operasyonel veri modelinizin** (örn: Askeri Raporlar, Radar/Sensör İzleri, İstihbarat Belgeleri, Personel Kayıtları, Güvenlik İhbarları vb.) projeye **uçtan uca nasıl entegre edileceğini** adım adım açıklamaktadır.

---

## 📑 İçindekiler
1. [Mimariye Genel Bakış ve Veri Akışı](#1-mimariye-genel-bakış-ve-veri-akışı)
2. [Adım 1: Alan Sınıflandırma Stratejisi (Field Classification Matrix)](#2-adım-1-alan-sınıflandırma-stratejisi-field-classification-matrix)
3. [Adım 2: Veritabanı Katmanı (PostgreSQL 17 & SQL Tasarımı)](#3-adım-2-veritabanı-katmanı-postgresql-17--sql-tasarımı)
4. [Adım 3: Kafka & Olay Tüketim Katmanı (Event Ingestion)](#4-adım-3-kafka--olay-tüketim-katmanı-event-ingestion)
5. [Adım 4: OpenSearch 3.8.0 Katmanı (İndeks, Mapping, Arama ve Filtreleme)](#5-adım-4-opensearch-380-katmanı-indeks-mapping-arama-ve-filtreleme)
6. [Adım 5: İndeksleme Pipeline'ı ve Vektör Optimizasyonu (Indexing Service)](#6-adım-5-indeksleme-pipelineı-ve-vektör-optimizasyonu-indexing-service)
7. [Adım 6: Arama ve İstemci / DTO Katmanı (Search API & UI DTOs)](#7-adım-6-arama-ve-istemci--dto-katmanı-search-api--ui-dtos)
8. [Adım 7: Web Arayüzü (React Vite UI) Uyarlaması](#8-adım-7-web-arayüzü-react-vite-ui-uyarlaması)
9. [Adım 8: Demo "Olaylar" Servislerinin Yönetimi (Temizlik & İzolasyon)](#9-adım-8-demo-olaylar-servislerinin-yönetimi-temizlik--izolasyon)
10. [Adım 9: Uçtan Uca Somut Örnek Senaryo: `RadarHedefKaydi` (Radar Track)](#10-adım-9-uçtan-uca-somut-örnek-senaryo-radarhedefkaydi-radar-track)
11. [Adım 10: 10 Adımlık Geliştirici Kontrol Listesi (Checklist)](#11-adım-10-10-adımlık-geliştirici-kontrol-listesi-checklist)


---

## 1. Mimariye Genel Bakış ve Veri Akışı

Yeni verinizin Kafka'dan başlayıp arama ekranına kadar izlediği yol şöyledir:

```
┌─────────────────┐
│ KAFKA TOPIC     │ ──► [SearchEventProcessor] ──► Doğrulama & Sürüm Kontrolü
│ (Yeni Veri JSON)│
└─────────────────┘              │
                                 ▼
                     [JsonSearchEventMapper] ────► Alanları Ayrıştırır:
                                 │                • SearchText (Embed Metni) Üretir
                                 │                • Filtreleri & Metadataları Çıkarır
                                 ▼
                    ┌─────────────────────────┐
                    │ POSTGRESQL 17           │
                    │ Tablo: yeni_domain_table│ ──► Ham veriyi ACID & Idempotent kaydeder
                    │ Tablo: indexing_state   │ ──► İndeksleme durumunu ve Hash'i saklar
                    └─────────────────────────┘
                                 │
                                 ▼
                    [BGE-M3 Embedding API] ────► Hash değiştiyse 1024-boyutlu vektör üretir
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │ OPENSEARCH 3.8.0        │
                    │ İndeks: yeni-index-name │ ──► BM25 Text + k-NN Vector + Filtreler
                    └─────────────────────────┘
                                 ▲
                                 │ (Sorgu Anı)
                    [SearchQueryService] ──────► BM25 + k-NN + RRF + Cross-Encoder Reranker
```

---

## 2. Adım 1: Alan Sınıflandırma Stratejisi (Field Classification Matrix)

Yeni veri türünüzün alanları (JSON key'leri) geldiğinde yapacağınız **İLK İŞ**, bu alanları aşağıdaki **4 temel kategoriye** ayırmaktır:

| Kategori | Tanım | OpenSearch Tipi | Örnek Alanlar | Nerede Tanımlanır? |
| :--- | :--- | :--- | :--- | :--- |
| **1. Search Fields (BM25)** | Kullanıcının kelime bazlı, eşanlamlı veya yazım hatalı arayacağı serbest metinler. | `text` + `turkish_search` | `baslik`, `aciklama`, `rapor_metni`, `tespit_notu` | `OpenSearchAdapter.java` (Mapping & `buildBm25QueryWithFilters`) |
| **2. Embed Fields (Vektör)** | BGE-M3 modeline gönderilip anlamsal tema çıkarılacak metinler. | Model çıktısı 1024-dim `knn_vector` (`embedding`) | Başlık + Özet + Detay metinlerinin birleşimi | `JsonSearchEventMapper.java` (`request.setSearchText(...)`) |
| **3. Filter Fields (Filtre)** | Kesin eşleşme, açılır menü (dropdown), tarih aralığı veya coğrafi alanlar. | `keyword`, `date`, `integer`, `geo_point` | `durum`, `seviye`, `kategori`, `kayit_zamani`, `konum` | `OpenSearchAdapter.java` (Mapping & `addFilters`) |
| **4. Metadata Fields (Bilgi)** | Arama/filtreye girmeyen, sadece kart tıklandığında ekranda gösterilecek teknik detaylar. | Serbest `object` / `keyword` | `kaynak_ip`, `dosya_yolu`, `ham_cihaz_kodu` | `SearchDocument.java` (`metadata` haritası) |

### ⚠️ Embedding İçin Altın Kurallar:
1. **ID ve Kodları Embedding Metnine Katmayın:** UUID'ler, TC kimlik numaraları, IP adresleri veya anlamsız cihaz seri numaraları embedding modelinin (BGE-M3) anlamsal dikkatini (attention) bozar. Bunları **kesinlikle** `keyword` yapıp filtre olarak kullanın.
2. **Kısa ve Anlamlı Cümleler:** `searchText` alanını oluştururken:
   ```java
   String searchText = "Başlık: " + baslik + ". Açıklama: " + aciklama + ". Kategori: " + kategori;
   ```
   formatında birleştirmek modelin semantik başarısını en üst düzeye çıkarır.

---

## 3. Adım 2: Veritabanı Katmanı (PostgreSQL 17 & SQL Tasarımı)

Mevcut sistemde PostgreSQL'de [`indexing_state`](file:///Users/macbookairm1/Desktop/semantic-search/sql/schema-complete.sql#L12) tablosu bulunmaktadır. Bu tablo zaten geneldir (`document_id`, `index_name`, `document_source JSON`). Ancak yeni veri modelinizin kendi operasyonel tablosu olmalıdır.

### 3.1. Yeni Tablo DDL (Flyway Migration)
`src/main/resources/db/migration/` altına yeni bir migration dosyası oluşturun (Örn: `V6__create_radar_kayitlari.sql`):

```sql
-- PostgreSQL 17 Uyumlu DDL
CREATE SEQUENCE IF NOT EXISTS radar_kayitlari_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS radar_kayitlari (
    id                  VARCHAR(128) PRIMARY KEY,       -- İş/Domain ID'si
    hedef_adi           VARCHAR(255) NOT NULL,
    hedef_tipi          VARCHAR(50)  NOT NULL,          -- ASKERI_GEMI, TICARI, IHA vb.
    durum               VARCHAR(50)  NOT NULL,          -- AKTIF, TAKIPTE, KAYIP
    hiz_knot            NUMERIC(6,2),
    irtifa_metre        INT,
    enlem               DOUBLE PRECISION,
    boylam              DOUBLE PRECISION,
    tespit_zamani       TIMESTAMPTZ  NOT NULL,
    rapor_metni         TEXT,
    ek_ozellikler       JSONB,                          -- Değişken dinamik alanlar
    created_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Hızlı filtreleme ve sorgu indeksleri
CREATE INDEX IF NOT EXISTS idx_radar_hedef_tipi   ON radar_kayitlari (hedef_tipi);
CREATE INDEX IF NOT EXISTS idx_radar_durum        ON radar_kayitlari (durum);
CREATE INDEX IF NOT EXISTS idx_radar_tespit_zaman ON radar_kayitlari (tespit_zamani DESC);
CREATE INDEX IF NOT EXISTS idx_radar_ek_ozellik   ON radar_kayitlari USING GIN (ek_ozellikler);
```

### 3.2. Idempotent UPSERT SQL Sorgusu
Kafka'dan aynı mesaj tekrar geldiğinde verinin ezilmemesi veya mükerrer oluşmaması için:

```sql
INSERT INTO radar_kayitlari (
    id, hedef_adi, hedef_tipi, durum, hiz_knot, irtifa_metre, enlem, boylam, tespit_zamani, rapor_metni, ek_ozellikler, updated_at
) VALUES (
    :id, :hedefAdi, :hedefTipi, :durum, :hizKnot, :irtifaMetre, :enlem, :boylam, :tespitZamani, :raporMetni, :ekOzellikler, NOW()
)
ON CONFLICT (id) DO UPDATE SET
    hedef_adi     = EXCLUDED.hedef_adi,
    durum         = EXCLUDED.durum,
    hiz_knot      = EXCLUDED.hiz_knot,
    irtifa_metre  = EXCLUDED.irtifa_metre,
    enlem         = EXCLUDED.enlem,
    boylam        = EXCLUDED.boylam,
    tespit_zamani = EXCLUDED.tespit_zamani,
    rapor_metni   = EXCLUDED.rapor_metni,
    ek_ozellikler = EXCLUDED.ek_ozellikler,
    updated_at    = NOW();
```

---

## 4. Adım 3: Kafka & Olay Tüketim Katmanı (Event Ingestion)

### 4.1. `application.yml` Rota Yapılandırması
Yeni Kafka topic'ini ve olay tiplerini [`src/main/resources/application.yml`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/resources/application.yml#L64-L85) altındaki `search.kafka.routes` bölümüne ekleyin:

```yaml
search:
  kafka:
    topics: ${SEARCH_KAFKA_TOPICS:radar-events,istihbarat-events}
    routes:
      # Yeni Veri Tipinizin Olay Rotaları:
      "[RADAR_TESPIT_EDILDI]":
        operation: UPSERT
        index-name: radar-kayitlari
        document-type: RADAR_HEDEF
      "[RADAR_GUNCELLENDI]":
        operation: UPSERT
        index-name: radar-kayitlari
        document-type: RADAR_HEDEF
      "[RADAR_SILINDI]":
        operation: DELETE
        index-name: radar-kayitlari
        document-type: RADAR_HEDEF
```

### 4.2. `JsonSearchEventMapper.java` Dosyasında Alan Eşleme
[`JsonSearchEventMapper.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/kafka/JsonSearchEventMapper.java#L70-L110) sınıfı, Kafka'dan gelen ham JSON'ı OpenSearch DTO'suna (`IndexDocumentRequest`) dönüştüren merkezdir.

Yeni verinizin alanlarını burada okuyup `request` içine yerleştirin:

```java
// JsonSearchEventMapper.java içinde mapDocument() metodu:

tools.jackson.databind.JsonNode data = event.data();

// 1. Temel Başlık ve Metinleri Ata:
if (data.has("hedef_adi")) {
    request.setTitle(data.get("hedef_adi").asText());
}
if (data.has("rapor_metni")) {
    request.setLongText(data.get("rapor_metni").asText());
}

// 2. Filtre Alanlarını Ata:
Map<String, Object> structured = new HashMap<>();
if (data.has("hedef_tipi")) structured.put("hedef_tipi", data.get("hedef_tipi").asText());
if (data.has("durum")) structured.put("durum", data.get("durum").asText());
if (data.has("hiz_knot")) structured.put("hiz_knot", data.get("hiz_knot").asDouble());
request.setStructuredFields(structured);

// 3. Tarih ve Coğrafi Konum:
if (data.has("tespit_zamani")) {
    request.setTarih(data.get("tespit_zamani").asText());
}
if (data.has("enlem") && data.has("boylam")) {
    // OpenSearch formatı: "lat,lon" veya {lat: ..., lon: ...}
    request.setKonum(data.get("enlem").asDouble() + "," + data.get("boylam").asDouble());
}

// 4. EMBEDDING METNİ OLUŞTURMA (En Önemli Kısım!):
// Modelin semantik arama yapabilmesi için aranacak metni burada harmanlayın:
StringBuilder sb = new StringBuilder();
if (request.getTitle() != null) sb.append("Hedef: ").append(request.getTitle()).append(". ");
if (data.has("hedef_tipi")) sb.append("Tip: ").append(data.get("hedef_tipi").asText()).append(". ");
if (request.getLongText() != null) sb.append(request.getLongText());

request.setSearchText(sb.toString().trim());
```

---

## 5. Adım 4: OpenSearch 3.8.0 Katmanı (İndeks, Mapping, Arama ve Filtreleme)

Tüm OpenSearch işlemleri tek bir sınıfta toplanmıştır: [`OpenSearchAdapter.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/client/opensearch/OpenSearchAdapter.java).

### 5.1. Yeni İndeks Şablonu Tanımlama (`createIndexIfNotExists`)
[`OpenSearchAdapter.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/client/opensearch/OpenSearchAdapter.java#L126-L163) satırındaki mapping bloğuna yeni alanlarınızı ekleyin:

```java
.mappings(m -> m
    // 1. Kimlik ve Tip
    .properties("id", p -> p.keyword(k -> k))
    .properties("type", p -> p.keyword(k -> k))

    // 2. Search Fields (BM25 Türkçe Analizörlü)
    .properties("title", p -> p.text(t -> t
            .analyzer("turkish_search")
            .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))))
    .properties("searchText", p -> p.text(t -> t.analyzer("turkish_search")))
    .properties("longText", p -> p.text(t -> t.analyzer("turkish_search")))

    // 3. Yeni Domain Filtre Alanlarınız:
    .properties("hedef_tipi", p -> p.keyword(k -> k))     -- Kesin eşleşme
    .properties("durum", p -> p.keyword(k -> k))          -- Dropdown filtre
    .properties("hiz_knot", p -> p.double_(d -> d))       -- Sayısal aralık (range)
    .properties("tarih", p -> p.date(d -> d))             -- Tarih aralığı (range)
    .properties("konum", p -> p.geoPoint(gp -> gp))       -- Coğrafi mesafe (geo_distance)

    // 4. BGE-M3 Yoğun Vektör (Dense Vector - 1024 Boyutlu HNSW Cosine)
    .properties("embedding", p -> p.knnVector(knn -> knn
            .dimension(1024)
            .method(method -> method
                    .name("hnsw")
                    .spaceType("cosinesimil")
                    .engine("faiss")
                    .parameters(Map.of("ef_construction", JsonData.of(256), "m", JsonData.of(16)))
            )
    ))
    .dynamic(DynamicMapping.True) -- Diğer dinamik alanlar bozulmadan saklanır
)
```

### 5.2. BM25 Arama Alanlarını ve Ağırlıklarını Belirleme
[`OpenSearchAdapter.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/client/opensearch/OpenSearchAdapter.java#L689) içindeki `buildBm25QueryWithFilters` metodunda arama yapılacak alanları ve boosting (`^çarpan`) değerlerini belirleyin:

```java
boolBuilder.must(m -> m
    .multiMatch(mm -> mm
        .query(queryText)
        // Başlıkta geçerse 4 kat, hedef tipinde geçerse 2 kat, raporda geçerse 1 kat puan ver:
        .fields("title^4.0", "hedef_tipi^2.0", "searchText^1.5", "longText^1.0")
        .analyzer("turkish_search")
        .fuzziness("AUTO:5,8")
    )
);
```

### 5.3. Filtreleme Mantığı (`addFilters`)
[`OpenSearchAdapter.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/client/opensearch/OpenSearchAdapter.java#L736-L790) içindeki `addFilters` metoduna yeni filtre kurallarınızı ekleyin:

```java
// Sayısal aralık filtresi örneği (hiz_knot):
if (filters.containsKey("minHiz") || filters.containsKey("maxHiz")) {
    boolBuilder.filter(f -> f.range(r -> {
        var rb = r.field("hiz_knot");
        if (filters.containsKey("minHiz")) rb.gte(JsonData.of(Double.parseDouble(filters.get("minHiz").toString())));
        if (filters.containsKey("maxHiz")) rb.lte(JsonData.of(Double.parseDouble(filters.get("maxHiz").toString())));
        return rb;
    }));
}
```

---

## 6. Adım 5: İndeksleme Pipeline'ı ve Vektör Optimizasyonu (Indexing Service)

[`IndexingService.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/service/IndexingService.java) sınıfınız hazır olarak harika bir performans optimizasyonuna sahiptir:
* Bir kayıt güncellendiğinde (`updateDocument`), dokümanın `searchText` içeriğinin SHA-256 hash'ini (`searchTextHash`) hesaplar.
* **Eğer metin değişmediyse (sadece durum, hız veya konum gibi alanlar güncellendiyse):** BGE-M3 modeline tekrar HTTPS isteği atmaz! Eski vektörü yeniden kullanır.
* **Eğer metin değiştiyse:** Uzaktaki model sunucunuza API Key ile istek atıp yeni 1024 boyutlu vektörünü alır.

Bu sayede airgap ortamdaki GPU/model sunucunuz gereksiz yere yorulmaz.

---

## 7. Adım 6: Arama ve İstemci / DTO Katmanı (Search API & UI DTOs)

Arama sonuçlarının frontend'e veya çağıran sisteme aktarılması için:

1. **[`SearchResult.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/dto/SearchResult.java)**:
   * Ekrandaki sonuç kartında yeni veri modelinize ait alanların görünmesi gerekiyorsa (örn: `hizKnot`, `hedefTipi`), bu alanları getter/setter ile ekleyin ya da doğrudan `structuredFields` haritasından okuyun.
2. **[`SearchRequest.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/dto/SearchRequest.java)**:
   * Kullanıcının arama yaparken gönderebileceği yeni filtre parametrelerini `filters` haritasına bağlayın:
     ```json
     {
       "query": "şüpheli hücumbot",
       "indexName": "radar-kayitlari",
       "searchType": "HYBRID",
       "filters": {
         "hedef_tipi": "ASKERI_GEMI",
         "minHiz": 25.0
       }
     }
     ```
3. **Varsayılan İndeks Adı Değişikliği (`.env` ve `application.yml`)**:
   * Uygulamanın her yerde otomatik olarak yeni indeksinizi kullanması için [`.env`](file:///Users/macbookairm1/Desktop/semantic-search/.env) dosyanıza şu satırı ekleyin:
     ```properties
     SEARCH_DEFAULT_INDEX=radar-kayitlari
     ```
   * Böylece hem REST API (`/api/search/hybrid`) hem de UI, istekte özel bir indeks adı belirtilmediğinde doğrudan yeni indeksinizi sorgular.

---

## 8. Adım 7: Web Arayüzü (React Vite UI) Uyarlaması

Projenizin `frontend/` dizinindeki React uygulaması, sonuçları dinamik olarak render edecek şekilde kurgulanmıştır:

### Seçenek A: Sıfır Kod Değişikliği (Kanonik Eşleme - Önerilen)
Eğer `JsonSearchEventMapper.java` içinde yeni verinizi projenin temel alanlarına eşlerseniz:
* `title` ➡️ Yeni varlığınızın adı/başlığı (`hedef_adi`, `rapor_konusu` vb.)
* `shortText` ➡️ Kısa özet metni
* `longText` ➡️ Tam rapor veya açıklama metni
* `birim` ➡️ Kategori, kaynak veya hedef tipi (`hedef_tipi`, `istihbarat_sinifi` vb.)
* `tarih` ➡️ Tespit veya rapor zamanı (`tespit_zamani`)
* `konum` ➡️ Enlem/Boylam koordinatı

**React arayüzünde tek bir satır kod bile değiştirmeden** arama sonuçları, kök kelime eşleşmeleri (term chips), benzerlik çubukları (score bars) ve detay açılır pencereleri kusursuzca çalışır!

### Seçenek B: React Ekranına Özel Rozet ve Alanlar Ekleme
Eğer sonuç kartlarının üzerinde özel alanlar (örn: `Hız: 38 knot`, `İrtifa: 1500m`) göstermek isterseniz:
1. [`frontend/src/types.ts`](file:///Users/macbookairm1/Desktop/semantic-search/frontend/src/types.ts#L1-L17) içine yeni alanlarınızı ekleyin:
   ```typescript
   export type SearchDocument = {
     id: string;
     title?: string | null;
     hiz_knot?: number | null;     // Yeni alan
     hedef_tipi?: string | null;   // Yeni alan
     // ...
   };
   ```
2. [`frontend/src/App.tsx`](file:///Users/macbookairm1/Desktop/semantic-search/frontend/src/App.tsx#L701-L711) dosyasındaki kart meta bloğuna rozet ekleyin:
   ```tsx
   {doc.hiz_knot && <span className="unit-badge">⚡ {doc.hiz_knot} knot</span>}
   ```

---

## 9. Adım 8: Demo "Olaylar" Servislerinin Yönetimi (Temizlik & İzolasyon)

Projede geliştirme aşamasında kullanılan demo bileşenlerin akıbeti:
1. **`olaylar.json` (16 MB)**: Demo veri dosyasıdır. Airgap ortamda diskte yer kaplamaması için silebilirsiniz (`rm src/main/java/com/example/semantic_search/olaylar.json`).
2. **`OlaylarIngestionService.java` ve `OlaylarController.java`**: 
   - İsteğe bağlı olarak bu sınıfları silebilirsiniz ya da yeni verinizi dosya üzerinden toplu yüklemek (batch import) istediğinizde şablon olarak kullanabilirsiniz.
   - Gerçek ortamda tüm veri akışı Kafka üzerinden asenkron geleceği için bu kontrolcüyü kullanmanıza gerek kalmaz.

---

## 10. Adım 9: Uçtan Uca Somut Örnek Senaryo: `RadarHedefKaydi` (Radar Track)

Gelin tüm bu adımları baştan sona somut bir örnekle görelim:

### 1. Kafka'ya Düşen Ham JSON Mesajı (`radar-events` topic'i):
```json
{
  "eventId": "evt-20260923-001",
  "eventType": "RADAR_TESPIT_EDILDI",
  "documentId": "RDR-TRK-9812",
  "version": 1,
  "data": {
    "hedef_adi": "Bilinmeyen Sürat Teknesi",
    "hedef_tipi": "BOT",
    "durum": "TAKIPTE",
    "hiz_knot": 38.5,
    "irtifa_metre": 0,
    "enlem": 41.2541,
    "boylam": 29.0822,
    "tespit_zamani": "2026-09-23T18:45:00Z",
    "rapor_metni": "Karadeniz açıklarında radar izi tespit edildi. AIS yayını kapalı, yüksek hızda seyrediyor."
  }
}
```

### 2. Sistemin Otomatik İşletme Sırası:
1. `KafkaIndexingConfiguration` mesajı `radar-events` topic'inden okur.
2. `SearchEventProcessor` mesajı yakalar, PostgreSQL `indexing_state` üzerinden versiyon kontrolü yapar.
3. `JsonSearchEventMapper`:
   - `SearchText` üretir: `"Hedef: Bilinmeyen Sürat Teknesi. Tip: BOT. Karadeniz açıklarında radar izi tespit edildi..."`
4. `IndexingService`:
   - BGE-M3 REST API'ye (`/v1/embeddings`) gidip 1024 elemanlı float vektörünü alır.
5. `OpenSearchAdapter`:
   - Dokümanı `radar-kayitlari` indeksine hem metin hem de `knn_vector` olarak yazar.
6. `SearchQueryLogService`:
   - PostgreSQL `search_query_log` tablosuna arama metriklerini kaydeder.

### 3. Kullanıcı REST Arama Sorgusu (cURL):
```bash
curl -X POST http://localhost:8080/api/search/hybrid \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Karadeniz şüpheli bot transponder kapalı",
    "indexName": "radar-kayitlari",
    "limit": 5,
    "filters": {
      "hedef_tipi": "BOT",
      "lat": 41.25,
      "lon": 29.08,
      "radiusKm": 20
    }
  }'
```
*Sistem önce OpenSearch'te BM25 ve k-NN aramalarını paralel çalıştırır, RRF ile birleştirir, uzaktaki Cross-Encoder Reranker modeline gönderip en alakalı hedefi 1. sıraya koyar.*

---

## 11. Adım 10: 10 Adımlık Geliştirici Kontrol Listesi (Checklist)

Yeni veri türünüzü projeye eklerken sırasıyla bu listeyi takip edin:

- [ ] **1. Alan Analizi**: Yeni veri alanlarını 4 kategoriye (Search, Embed, Filter, Metadata) ayırın.
- [ ] **2. PostgreSQL DDL**: `src/main/resources/db/migration/` altına yeni tablonuzu ve indekslerinizi içeren `V..__create_table.sql` scriptini ekleyin.
- [ ] **3. JPA / Repository**: Yeni tablo için Entity ve Repository sınıflarını oluşturun.
- [ ] **4. `application.yml` Rotaları**: `search.kafka.routes` altına yeni olay adlarını (`[YENI_CREATED]`) ve hedef indeks adını yazın.
- [ ] **5. Topic Tanımı**: `.env` içindeki `SEARCH_KAFKA_TOPICS` değerine yeni Kafka topic adını ekleyin.
- [ ] **6. JSON Mapper**: [`JsonSearchEventMapper.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/kafka/JsonSearchEventMapper.java) içine girip gelen JSON'dan alanları okuyun ve semantik `searchText` türetme kuralını yazın.
- [ ] **7. OpenSearch Mapping**: [`OpenSearchAdapter.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/client/opensearch/OpenSearchAdapter.java) içinde `createIndexIfNotExists` metoduna yeni alan tiplerini (`text`, `keyword`, `date`, `geo_point`) ekleyin.
- [ ] **8. BM25 Query Fields**: [`OpenSearchAdapter.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/client/opensearch/OpenSearchAdapter.java) içinde `buildBm25QueryWithFilters` metodunda ağırlıklı aranacak alanları belirleyin (`fields("baslik^3.0", ...)`).
- [ ] **9. Dinamik Filtreler**: [`OpenSearchAdapter.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/client/opensearch/OpenSearchAdapter.java) içinde `addFilters` metoduna yeni alan filtrelerinizi ekleyin.
- [ ] **10. Derleme ve Test**: Terminalde `./mvnw clean compile` çalıştırıp hatasız derlendiğini teyit edin.

