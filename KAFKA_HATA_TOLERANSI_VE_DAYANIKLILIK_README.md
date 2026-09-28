# KAFKA TÜKETİM VE HATA DAYANIKLILIK REHBERİ
## Mesaj Yaşam Döngüsü, Hata Toleransı ve Sıfır Veri Kaybı Mimarisi

Bu doküman; Kafka'dan tüketilen bir mesajın işlenmesi sırasında yaşanabilecek tüm hata senaryolarını (OpenSearch çökmesi, embedding servisi arızası, pod ölümü, bozuk mesajlar, ağ kopması vb.), bu senaryolarda mesajın yok olup olmayacağını ve sistemimizin bu senaryolara ne derece dayanıklı olduğunu **doğrudan kaynak kod ve konfigürasyon referanslarıyla** açıklamaktadır.

---

## 1. Temel İlke: "Mesaj Asla Sessizce Yok Olmaz"

Sistem mimarisinde **At-Least-Once Delivery (En Az Bir Kez Teslimat)** ve **Fail-Safe (Hata Güvenli)** modeli uygulanmıştır.

Kafka'da bir mesajın `poll()` / `consume` edilmesi, o mesajın Kafka'dan silindiği veya işlendiği anlamına **gelmez**. Bir mesajın Kafka tarafında "başarıyla tamamlandı" sayılabilmesi için tüketici uygulamanın Kafka Broker'a **Offset Commit (Okundu Onayı)** iletmesi zorunludur.

### Kod Tabanındaki 2 Temel Güvenlik Kilidi:

1. **Otomatik Onayın Kapatılması (`enable-auto-commit: false`):**
   - **Referans:** [`src/main/resources/application.yml`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/resources/application.yml) (Satır 13)
   - Kafka'nın varsayılan olarak belirli aralıklarla otomatik commit yapması engellenmiştir. Mesaj iş mantığı tamamlanmadan asla "okundu" sayılmaz.

2. **Kayıt Bazlı ve Başarı Koşullu Onay (`AckMode.RECORD`):**
   - **Referans:** [`KafkaIndexingConfiguration.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/config/KafkaIndexingConfiguration.java) (Satır 131)
   - `factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);`
   - Spring Kafka dinleyicisi ([`KafkaIndexingListener.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/kafka/KafkaIndexingListener.java) - `listen()` metodu), mesajı baştan sona **sıfır istisna (exception) ile** bitirmeden Kafka'ya onay göndermez. Bir hata fırlatılırsa onay iptal edilir ve hata yönetimi devreye girer.

---

## 2. Mimari Bileşenler ve Görevleri

```
                +-------------------+
                |   Kafka Broker    |
                +--------+----------+
                         |
                         | 1. poll()
                         v
              +----------------------+
              | KafkaIndexingListener|
              +----------+-----------+
                         |
                         | 2. process()
                         v
              +----------------------+
              | SearchEventProcessor |
              +----------+-----------+
                         |
         +---------------+---------------+
         |                               |
         | 3. Lock & Version             | 4. Update
         v                               v
+------------------+           +-------------------+
| PostgreSQL (DB)  |           |  IndexingService  |
|  IndexingState   |           +---------+---------+
+------------------+                     |
                         +---------------+---------------+
                         |                               |
                         v                               v
              +--------------------+           +-------------------+
              |  EmbeddingProvider |           | OpenSearchAdapter |
              |  (Ollama / Local)  |           |   (OpenSearch)    |
              +--------------------+           +-------------------+
                         |                               |
                   [HATA OLURSA]                   [HATA OLURSA]
                         +---------------+---------------+
                                         |
                                         v
                         +-------------------------------+
                         |   searchKafkaErrorHandler     |
                         |     (DefaultErrorHandler)     |
                         +---------------+---------------+
                                         |
                         +---------------+---------------+
                         | Retry (2 defa, 1 sn arayla)   |
                         | Başarısız ise:               |
                         | DeadLetterPublishingRecoverer |
                         +---------------+---------------+
                                         |
                                         v
                         +-------------------------------+
                         | Dead-Letter Topic (DLT)       |
                         |   (olaylar.indexing.errors)   |
                         +-------------------------------+
```

---

## 3. Senaryo Analizleri: Hangi Durumda Ne Olur? Dayanıklı mıyız?

---

### Senaryo 1: OpenSearch Kapalı / Çökmüş / Ağ Timeout Alıyor

* **Olay:** Mesaj tüketildi, JSON okundu, embedding hazırlandı. Doküman OpenSearch'e gönderilirken OpenSearch çöktü veya ağ koptu.
* **Kod Akışı:**
  1. [`OpenSearchAdapter.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/client/opensearch/OpenSearchAdapter.java) (Satır 257-259): `client.index(...)` `IOException` fırlatır ve metot bunu `OpenSearchUnavailableException` sarmalayarak yukarı atar.
  2. [`IndexingService.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/service/IndexingService.java) ve [`SearchEventProcessor.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/service/SearchEventProcessor.java) üzerinden hata [`KafkaIndexingListener.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/kafka/KafkaIndexingListener.java) `listen()` metodunu kırar.
  3. [`KafkaIndexingConfiguration.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/config/KafkaIndexingConfiguration.java) (Satır 109): `DefaultErrorHandler` devreye girer. `new FixedBackOff(properties.getRetryDelayMs(), properties.getRetryAttempts())` ayarı çalışır:
     - 1. deneme başarısız oldu.
     - 1000 ms (1 saniye) bekler. 2. kez dener.
     - Yine hata alırsa 1000 ms bekler. 3. kez dener.
  4. Eğer OpenSearch ayağa kalkmazsa: `DeadLetterPublishingRecoverer` (Satır 101) mesajı orijinal içeriği, hata sebebi ve stacktrace bilgisiyle birlikte `olaylar.indexing.errors` konusuna (DLT) yazar.
  5. Mesaj DLT'ye yazıldıktan sonra ana kuyruğun offset'i ilerletilir (Kuyruk tıkanması engellenir).
* **Dayanıklılık Durumu:** **%100 DAYANIKLI.**
* **Mesaj Yok Oldu mu?:** **HAYIR.** Mesaj DLT kuyruğunda eksiksiz saklanır. OpenSearch ayağa kaldırıldığında DLT'den yeniden oynatılabilir (replay).

---

### Senaryo 2: Embedding Sunucusu (Air-Gap / GPU Sunucusu) Çöktü veya Yanıt Vermiyor

* **Olay:** Mesaj tüketildi, ancak metin vektörü oluşturulurken embedding servisi (Ollama veya Python FastAPI) çöktü veya zaman aşımına uğradı.
* **Kod Akışı:**
  1. [`IndexingService.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/service/IndexingService.java) (Satır 131 & 244): `embeddingProvider.generateEmbedding(searchText)` çağrılır.
  2. HTTP bağlantı hatası veya timeout durumunda `EmbeddingServiceUnavailableException` (veya `ResourceAccessException`) fırlatılır.
  3. `DefaultErrorHandler` hatayı yakalar ve 1 saniye aralıklarla 2 kez yeniden dener.
  4. Embedding servisi düzelmezse mesaj güvenle DLT konusuna (`olaylar.indexing.errors`) yönlendirilir.
* **Dayanıklılık Durumu:** **%100 DAYANIKLI.**
* **Mesaj Yok Oldu mu?:** **HAYIR.** DLT'ye aktarılır.

---

### Senaryo 3: Bozuk JSON / Şema Uyuşmazlığı / Eksik Zorunlu Alan ("Zehirli Mesaj" - Poison Pill)

* **Olay:** Kafka konusuna bozuk karakterler içeren bir string veya şemaya uymayan (örneğin zorunlu `documentId` alanı eksik) bir JSON mesajı atıldı.
* **Kod Akışı:**
  1. [`SearchEventProcessor.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/service/SearchEventProcessor.java):
     - `mapper.read(message)` parsing hatası verirse veya `validate(event)` Jakarta doğrulamasını geçemezse `InvalidSearchEventException` fırlatılır (Satır 61, 79, 87, 123).
  2. [`KafkaIndexingConfiguration.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/config/KafkaIndexingConfiguration.java) (Satır 111):
     ```java
     errorHandler.addNotRetryableExceptions(InvalidSearchEventException.class);
     ```
     **Kritik Mimari Detay:** Bozuk bir JSON'u 100 kere yeniden denemek sonucu değiştirmeyeceğinden, sistem bunun **yeniden denenemez (non-retryable)** bir hata olduğunu bilir!
  3. Yeniden deneme ile vakit kaybetmeden **anında DLT'ye aktarılır**.
* **Dayanıklılık Durumu:** **MÜKEMMEL DÜZEYDE TASARLANMIŞ DAYANIKLI.**
* **Faydası:** Bozuk tek bir mesaj tüm tüketici kuyruğunu kilitletemez (Head-of-Line Blocking engellenir). Diğer sağlıklı mesajlar hızla işlenmeye devam eder; bozuk mesaj DLT'de incelenmek üzere saklanır.

---

### Senaryo 4: Pod / Uygulama İşlemin Tam Ortasında Çöktü (OOMKilled / SIGKILL / Elektrik Kesintisi)

* **Olay:** Mesaj Kafka'dan alındı. Embedding üretildi, tam OpenSearch'e yazılırken ya da yazıldığı saniyede sunucu kapandı / JVM çöktü.
* **Kod Akışı:**
  1. Offset onayı `AckMode.RECORD` gereği yalnızca `listen()` metodunun başarıyla sonlanmasından sonra gerçekleştiği için, JVM aniden öldüğünde Kafka Broker'a **Offset Commit GİTMEZ**.
  2. Kafka Broker, bu tüketicinin heartbeat sinyalini kaybedince tüketiciyi gruptan düşürür.
  3. Pod yeniden başladığında (veya Kubernetes diğer pod'a görevi devrettiğinde), Kafka Broker o mesajı **kaldığı offset'ten tekrar teslim eder (At-Least-Once Delivery)**.
* **Dayanıklılık Durumu:** **%100 DAYANIKLI.**
* **Mesaj Yok Oldu mu?:** **HAYIR.** Kafka Broker mesajı güvenle elinde tutmaya devam eder.

---

### Senaryo 5: Mesajın İki Kez Gelmesi (Duplicate / Replay) veya Eski Mesajın Geç Gelmesi (Out-of-Order / Stale Event)

* **Olay:** Senaryo 4 gerçekleştiği için bir mesaj iki kere tüketildi. Veya ağ gecikmesi nedeniyle v2 sürümündeki olay önce işlendi, geciken v1 sürümü daha sonra geldi.
* **Kod Akışı:**
  1. [`SearchEventProcessor.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/service/SearchEventProcessor.java) (Satır 92-102):
     ```java
     IndexingState state = repository.findByDocumentIdAndIndexNameForUpdate(event.documentId(), indexName)...
     if (state.getLastEventVersion() != null && event.version() <= state.getLastEventVersion()) {
         return Outcome.STALE;
     }
     ```
  2. PostgreSQL tablosundaki ilgili satır `PESSIMISTIC_WRITE` ile kilitlenir.
  3. Gelen mesajın sürümü veritabanındaki sürümden küçük veya eşitse, sistem bunu anlar, işlemi `STALE` olarak işaretler ve OpenSearch'e **tekrar yazmadan güvenle atlar**.
* **Dayanıklılık Durumu:** **%100 DAYANIKLI (Tam Idempotency / Çift Yazma Koruması).**
* **Sonuç:** OpenSearch'teki yeni ve güncel verinin üzerine yanlışlıkla eski verinin yazılması imkansızdır.

---

### Senaryo 6: PostgreSQL Veritabanı Çöktü (IndexingState Tablosu Erişilemez)

* **Olay:** OpenSearch çalışıyor fakat PostgreSQL bağlantısı koptu.
* **Kod Akışı:**
  1. [`SearchEventProcessor.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/service/SearchEventProcessor.java) (Satır 92): `repository.findByDocumentIdAndIndexNameForUpdate` çağrısı Spring Data JPA / Hibernate bağlantı hatası fırlatır.
  2. `DefaultErrorHandler` devreye girer; 2 kez yeniden dener. Düzelmezse mesajı DLT kuyruğuna aktarır.
* **Dayanıklılık Durumu:** **%100 DAYANIKLI.**
* **Mesaj Yok Oldu mu?:** **HAYIR.**

---

### Senaryo 7: Dead-Letter Topic (DLT)'nin Kendisine Yazılamaması (DLT Broker Hatası)

* **Olay:** OpenSearch kapalı, retry bitti. Sistem mesajı DLT'ye (`olaylar.indexing.errors`) yazmak istiyor fakat Kafka DLT partition'ında disk doldu veya Kafka erişimi koptu.
* **Kod Akışı:**
  1. [`KafkaIndexingConfiguration.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/config/KafkaIndexingConfiguration.java) (Satır 103-104):
     ```java
     recoverer.setFailIfSendResultIsError(true);
     recoverer.setWaitForSendResultTimeout(Duration.ofMillis(properties.getPublishTimeoutMs()));
     ```
     **Hayati Güvenlik Önlemi:** Birçok sistemde DLT'ye yazma başarısız olursa hata yutulur ve mesaj kaybedilir. Projenizde `setFailIfSendResultIsError(true)` açıkça belirtilmiştir!
  2. DLT'ye yazılamazsa `DeadLetterPublishingRecoverer` istisna fırlatır.
  3. Container container seviyesinde offset commit **YAPMAZ**.
  4. Tüketici durur ve mesaj orijinal ana kuyrukta kalır.
* **Dayanıklılık Durumu:** **EN ÜST DÜZEY FAIL-SAFE.**
* **Mesaj Yok Oldu mu?:** **HAYIR.** DLT'ye yazılamayan hiçbir mesaj onaylanıp kuyruktan düşürülmez.

---

### Senaryo 8: Kafka Broker ile Uygulama Arasındaki Ağ Koptu

* **Olay:** Spring uygulaması ile Kafka Broker arasındaki switch/network çöktü.
* **Kod Akışı:**
  1. Kafka Java Client arka planda `reconnect.backoff.ms` döngüsüyle sürekli yeniden bağlanmaya çalışır.
  2. `poll()` yapılamadığı için yeni mesaj çekilemez.
  3. O an işlenmekte olan mesaj varsa commit iletilemeyeceği için broker tarafında offset ilerlemez.
* **Dayanıklılık Durumu:** **%100 DAYANIKLI.**

---

### Senaryo 9: Kafka 24 Saatlik Saklama Süresi (Log Retention) ve Kuyruk Yığılması Riski

* **Olay:** Uygulama veya OpenSearch 24 saatten uzun süre kapalı kaldı. Kafka topic varsayılan retention süresi doldu.
* **Durum:** Kafka broker seviyesinde `log.retention.hours=24` ayarlıysa ve sistem 24 saat boyunca hiç ayağa kaldırılmazsa, Kafka broker tüketilmeyen mesajları diskten silebilir.
* **Dayanıklılık Durumu:** **DİKKAT EDİLMESİ GEREKEN ALTYAPI PARAMETRESİ.**
* **Önlem ve Yapılması Gereken:**
  - Topic oluşturulurken retention süresi en az 7 gün (168 saat) yapılmalıdır:
    `kafka-configs.sh --alter --entity-type topics --entity-name olaylar-events --add-config retention.ms=604800000`
  - [`application.yml`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/resources/application.yml) satır 14'te bulunan `auto-offset-reset: earliest` ayarımız sayesinde, pod açıldığı anda 7 günlük geçmiş verilerin tamamını baştan eksiksiz tüketir.

---

### Senaryo 10: DLT'ye Giden Hatalı Mesajlar Nasıl Geri Kazanılır? (Dead-Letter Replay)

* **Olay:** OpenSearch 2 saat kapalı kaldı ve 5.000 mesaj DLT'ye (`olaylar.indexing.errors`) yazıldı. OpenSearch ayağa kalktı. Bu 5.000 mesajı OpenSearch'e nasıl alacağız?
* **Çözüm:**
  - DLT konusu bağımsız bir Kafka konusudur. Mesajlar orada bekler.
  - Sisteme bir **Kafka DLT Replay Consumer** eklenerek veya basit bir Spring Batch / CLI scripti ile `olaylar.indexing.errors` konusundaki mesajlar okunup tekrar `olaylar-events` ana konusuna aktarılır.
  - Sürüm kontrolümüz (`version <= lastEventVersion`) sayesinde replay sırasında hiçbir çakışma veya veri bozulması yaşanmaz.

---

## 4. Hata ve Dayanıklılık Özet Matrisi

| No | Hata Durumu | Mesaj Kaybolur mu? | Otomatik Retry | DLT'ye Aktarım | İlgili Kod & Konfigürasyon |
| :---: | :--- | :---: | :---: | :---: | :--- |
| **1** | OpenSearch Bağlantı/Timeout Hatası | ❌ **HAYIR** | ✅ Var (2x, 1s) | ✅ `olaylar.indexing.errors` | `OpenSearchAdapter.java:257`, `KafkaIndexingConfiguration.java:109` |
| **2** | Embedding Servisi Çökmesi | ❌ **HAYIR** | ✅ Var (2x, 1s) | ✅ `olaylar.indexing.errors` | `IndexingService.java:244`, `KafkaIndexingConfiguration.java:109` |
| **3** | Bozuk JSON / Validasyon Hatası | ❌ **HAYIR** | ❌ Atlanır (Hızlı) | ✅ Doğrudan DLT | `SearchEventProcessor.java:79`, `addNotRetryableExceptions:111` |
| **4** | JVM / Pod Çökmesi (OOM / Crash) | ❌ **HAYIR** | 🔄 Pod açılınca | ❌ Ana kuyruktan tekrar | `AckMode.RECORD:131`, `enable-auto-commit: false` |
| **5** | Mükerrer / Eski Sürüm Mesaj | ❌ **HAYIR** | ❌ Gerek yok | ⏭️ Sessizce atlanır (Stale) | `SearchEventProcessor.java:100-102` (`version check`) |
| **6** | PostgreSQL / DB Çökmesi | ❌ **HAYIR** | ✅ Var (2x, 1s) | ✅ `olaylar.indexing.errors` | `SearchEventProcessor.java:92`, `KafkaIndexingConfiguration.java:109` |
| **7** | DLT Konusuna Yazılamaması | ❌ **HAYIR** | 🛑 İşlem durur | 🛑 Commit yapılmaz | `recoverer.setFailIfSendResultIsError(true):103` |
| **8** | Kafka Ağ Bağlantısı Kopması | ❌ **HAYIR** | 🔄 Sürekli Reconnect | 🛑 Broker'da bekler | Kafka Java Driver Reconnect Protocol |

---

## 5. Sonuç ve Özet

Kod tabanınızdaki kurgu, kurumsal düzeyde (enterprise-grade) bir veri tutarlılığına sahiptir:
1. **Mesaj tüketildiği an asla yok olmaz.**
2. **OpenSearch'e yazılamazsa önce bekleyip tekrar dener.**
3. **Sorun devam ederse DLT kuyruğuna taşır.**
4. **Sunucu kapansa dahi commit edilmediği için baştan okunur.**
5. **Hiçbir mesaj veri tabanınızda veya OpenSearch'te sessizce kaybolmaz.**
