# 🛰️ Kubernetes Kafka Bağlantı, Topic Dinleme ve Olay Tüketim Kılavuzu (Airgap)

Bu kılavuz; **kapalı devre (airgap / internetsiz) kurumsal ağda**, **Kubernetes (K8s) kümesinde Docker container'ları ile orkestre edilen mevcut Apache Kafka servisine** Windows makinenizden (veya sunucudan) bağlanmak, gerekli topic'leri dinlemek, gelen olayları (events) tüketerek OpenSearch ve PostgreSQL'e otomatik indekslemek için gereken tüm teknik adımları içermektedir.

---

## 📑 İçindekiler
1. [Genel Mimari ve Veri Akışı](#1-genel-mimari-ve-veri-akışı)
2. [Kubernetes Kafka Dış Bağlantı Mantığı (Advertised Listeners Püf Noktası)](#2-kubernetes-kafka-dış-bağlantı-mantığı-advertised-listeners-püf-noktası)
3. [Gerekli Topic'leri Belirleme ve Yapılandırma](#3-gerekli-topicleri-belirleme-ve-yapılandırma)
4. [Kafka Mesaj Sözleşmesi (JSON Veri Formatı)](#4-kafka-mesaj-sözleşmesi-json-veri-formatı)
5. [Olay Tipleri ve İndeks Yönlendirme (Event Routing)](#5-olay-tipleri-ve-indeks-yönlendirme-event-routing)
6. [Windows İstemci Tarafı `.env` Yapılandırması](#6-windows-istemci-tarafı-env-yapılandırması)
7. [Ağ ve Bağlantı Doğrulama Testleri (Windows PowerShell)](#7-ağ-ve-bağlantı-doğrulama-testleri-windows-powershell)
8. [Uygulamayı Başlatma ve Tüketim Logları](#8-uygulamayı-başlatma-ve-tüketim-logları)
9. [Dağıtık Idempotency (Çift Kayıt ve Sırasız Veri Koruması)](#9-dağıtık-idempotency-çift-kayıt-ve-sırasız-veri-koruması)
10. [Sık Karşılaşılan Kubernetes-Kafka Sorunları ve Çözümleri](#10-sık-karşılaşılan-kubernetes-kafka-sorunları-ve-çözümleri)

---

## 1. Genel Mimari ve Veri Akışı

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ KUBERNETES KÜMESİ (Airgap İç Ağ)                                                 │
│                                                                                  │
│   ┌────────────────────────────────────────────────────────────────────────┐     │
│   │ Kafka Pod Cluster (Docker Images / KRaft or Zookeeper)                 │     │
│   │                                                                        │     │
│   │  • Topic: olaylar-events (veya özel topicleriniz)                      │     │
│   │  • Broker 0 (Port: 9092 / NodePort: 30092)                             │     │
│   │  • Broker 1 (Port: 9092 / NodePort: 30093)                             │     │
│   └────────────────────────────────────────────────────────────────────────┘     │
└──────────────────────────┬───────────────────────────────────────────────────────┘
                           │ TCP (External NodePort / LoadBalancer IP)
                           ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│ WINDOWS MAKİNE (Semantic Search Spring Boot Uygulaması)                          │
│                                                                                  │
│  1. KafkaIndexingListener: Mesajı kuyruktan çeker (Consumer Group)               │
│  2. JsonSearchEventMapper: JSON'ı ayrıştırır (zarflı veya düz JSON destekler)    │
│  3. SearchEventProcessor: Bellek içi versiyon kontrolü yapar (Idempotency)       │
│  4. IndexingService: Doğrudan OpenSearch'e (vektör + BM25) indeksler (DB Yok)    │
│  5. Offset Commit: Başarıyla işlenen mesaj Kafka'da onaylanır (AckMode.RECORD)   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Kubernetes Kafka Dış Bağlantı Mantığı (Advertised Listeners Püf Noktası)

> [!CRITICAL]
> **KUBERNETES İÇİNDEKİ KAFKA'YA DIŞARIDAN BAĞLANIRKEN EN SIK YAŞANAN HATA:**  
> İstemci ilk olarak bootstrap adresine bağlanır; ancak Kafka broker'ı istemciye **`advertised.listeners`** adresini döner.  
> Eğer K8s yöneticiniz Kafka'yı sadece küme içi adresiyle (`kafka-0.kafka-headless.default.svc.cluster.local`) tanıttıysa, Windows makineniz bu iç DNS'i çözemez ve **`UnknownHostException`** veya **`Connection Timeout`** alırsınız.

### Kubernetes Dış Erişim Türleri:

#### Yöntem A: NodePort ile Erişim (En Yaygın Senaryo)
K8s kümesindeki Worker Node IP'si ve Kafka NodePort'u kullanılır:
* **Bootstrap Adresi**: `192.168.1.100:30092` (Örnek Worker IP ve Port)
* Broker'lar dışarıya Node IP'lerini anons eder.

#### Yöntem B: LoadBalancer / Ingress ile Erişim
Kurumsal K8s ortamlarında Kafka'nın önüne sabit bir iç IP veya LoadBalancer konur:
* **Bootstrap Adresi**: `192.168.1.150:9092` veya `kafka.k8s.lan:9092`

#### Yöntem C: Windows `hosts` Dosyası Çözümü (Eğer K8s DNS Hatası Alırsanız)
Eğer Kubernetes'teki Kafka broker'ları istemcinize pod/servis isimlerini (örn: `kafka-0.kafka-headless`) anons ediyorsa, Windows makinenizdeki `C:\Windows\System32\drivers\etc\hosts` dosyasına şu satırları ekleyerek sorunu hemen çözebilirsiniz:
```text
192.168.1.100  kafka-0.kafka-headless.default.svc.cluster.local kafka-0
192.168.1.101  kafka-1.kafka-headless.default.svc.cluster.local kafka-1
```

---

## 3. Gerekli Topic'leri Belirleme ve Yapılandırma

Spring Boot uygulamamız, dinlenecek topic'leri ve tüketici grubunu tamamen dinamik olarak yönetir.

### 3.1. Ortam Değişkenleri:
* **`SEARCH_KAFKA_TOPICS`**: Dinlenecek topic adı.
  * Tek bir topic için: `SEARCH_KAFKA_TOPICS=olaylar-events`
  * **Birden fazla topic dinlemek isterseniz** virgülle ayırabilirsiniz:  
    `SEARCH_KAFKA_TOPICS=olaylar-events,ihbarlar-events,saha-raporlari`
* **`KAFKA_GROUP_ID`**: Tüketici grup kimliği (Varsayılan: `semantic-search-indexer`).
  * Aynı `group-id` ile birden fazla uygulama ayağa kalkarsa Kafka mesajları aralarında otomatik paylaştırır (Load Balancing).
* **`KAFKA_AUTO_OFFSET_RESET`**:
  * `earliest` (Önerilen İlk Kurulum): Topic'te birikmiş en eski olaydan başlayarak hepsini geriye dönük tüketir ve OpenSearch'e indeksler.
  * `latest`: Sadece uygulama ayağa kalktıktan sonra Kafka'ya düşen yeni olayları dinler.
* **`SEARCH_KAFKA_DEAD_LETTER_TOPIC`**: Hatalı veya JSON formatına uymayan bozuk mesajların gönderileceği hata kuyruğu (Varsayılan: `olaylar.indexing.errors`).

---

## 4. Kafka Mesaj Sözleşmesi (JSON Veri Formatı)

Kubernetes'teki producer servislerinin Kafka topic'ine bastığı mesajlar standart bir JSON nesnesi olmalıdır. Projedeki [`SearchIndexingEvent.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/model/SearchIndexingEvent.java) ve [`JsonSearchEventMapper.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/kafka/JsonSearchEventMapper.java) bu sözleşmeyi ayrıştırır.

### Standart Olay JSON Örneği:
```json
{
  "eventId": "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
  "eventType": "OLAY",
  "documentId": "olay-10524",
  "version": 1,
  "data": {
    "id": "olay-10524",
    "title": "Karadeniz Açıkları Şüpheli Deniz Trafiği Raporu",
    "shortText": "Sahil Güvenlik unsurlarınca tespit edilen radar izi incelemesi.",
    "longText": "01.09.2026 tarihinde Karadeniz açıklarında rotasını bildirmeyen şüpheli bir ticari gemi tespit edilmiş, bölgeye sevk edilen botlar eşliğinde kimlik kontrolü icra edilmiştir.",
    "searchText": "Karadeniz Açıkları Şüpheli Deniz Trafiği Raporu Sahil Güvenlik radar izi ticari gemi kontrolü",
    "fields": {
      "type": "Deniz Güvenliği",
      "birim": "Karadeniz Bölge Komutanlığı",
      "adres": "Sinop Açıkları, Karadeniz",
      "konum": "42.0282, 35.1517",
      "tarih": "2026-09-01T14:30:00Z"
    }
  },
  "metadata": {
    "sourceSystem": "SAHA_KOMUTA_K8S",
    "createdAt": "2026-09-01T14:31:00Z"
  }
}
```

### Alan Açıklamaları:
| Alan | Tip | Zorunlu? | Açıklama |
| :--- | :--- | :--- | :--- |
| `eventId` | String (UUID) | **Evet** | Olayın tekil kimliği. PostgreSQL'de Idempotency takibinde kullanılır. |
| `eventType` | String | **Evet** | Olay tipi (`OLAY`, `IncidentCreated`, `OLAY_GUNCELLENDI`, `OLAY_SILINDI`). |
| `documentId`| String | **Evet** | İndekslenecek dökümanın ana iş kimliği (`entityId`). |
| `version` | Long | **Evet** | Artan sürüm no (1, 2, 3...). Eski/sırasız gelen Kafka olaylarının yeniyi ezmesini önler. |
| `data` | JSON Object | **Evet** | Döküman içeriği (`title`, `searchText`, `shortText`, `longText`, `fields`). |
| `metadata` | JSON Object | Hayır | Kaynak sistem bilgisi. `@JsonIgnoreProperties` sayesinde ek alanlar hataya yol açmaz. |

---

## 5. Olay Tipleri ve İndeks Yönlendirme (Event Routing)

Uygulamanın [`application.yml`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/resources/application.yml) dosyasındaki `search.kafka.routes` haritası, gelen olayın tipine göre OpenSearch üzerinde yapılacak işlemi belirler:

```yaml
search:
  kafka:
    routes:
      "[OLAY]":
        operation: UPSERT
        index-name: olaylar
        document-type: OLAY
      "[OLAY_BILDIRILDI]":
        operation: UPSERT
        index-name: olaylar
        document-type: OLAY
      "[OLAY_GUNCELLENDI]":
        operation: UPSERT
        index-name: olaylar
        document-type: OLAY
      "[IncidentCreated]":
        operation: UPSERT
        index-name: olaylar
        document-type: OLAY
      "[OLAY_SILINDI]":
        operation: DELETE
        index-name: olaylar
        document-type: OLAY
```

> 💡 **Kural**:  
> * **`UPSERT`**: Döküman OpenSearch'e gönderilir (BGE-M3 embedding üretilir, BM25 metni oluşturulur). Varsa güncellenir, yoksa yeni açılır.
> * **`DELETE`**: İlgili `documentId`, `olaylar` indeksinden anında silinir.

---

## 6. Windows İstemci Tarafı `.env` Yapılandırması

Windows makinenizdeki `.env` dosyasını açarak K8s ortamındaki Kafka bilgilerini girin:

### Senaryo 1: Güvenliksiz İç Ağ (PLAINTEXT - En Yaygın Airgap Senaryosu)
```properties
# ==============================================================================
# 4. KAFKA VERİ AKIŞI (Kubernetes Cluster İçi Kafka)
# ==============================================================================
# Kubernetes Worker Node IP'sini ve Kafka dış portunu yazın:
KAFKA_BOOTSTRAP_SERVERS=192.168.1.100:30092
KAFKA_GROUP_ID=semantic-search-indexer
KAFKA_AUTO_OFFSET_RESET=earliest

# Dinlenecek topic'ler:
SEARCH_KAFKA_ENABLED=true
SEARCH_KAFKA_TOPICS=olaylar-events
SEARCH_KAFKA_DEAD_LETTER_TOPIC=olaylar.indexing.errors
SEARCH_KAFKA_CONCURRENCY=2
```

### Senaryo 2: Kullanıcı Adı & Parola ile Kimlik Doğrulama (SASL_PLAINTEXT / SCRAM-SHA-512)
Eğer Kubernetes'teki Kafka SASL ile korunuyorsa:
```properties
KAFKA_BOOTSTRAP_SERVERS=192.168.1.100:30092
KAFKA_GROUP_ID=semantic-search-indexer
KAFKA_AUTO_OFFSET_RESET=earliest

# SASL Yapılandırması:
SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_PLAINTEXT
SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=SCRAM-SHA-512
SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=org.apache.kafka.common.security.scram.ScramLoginModule required username="search_user" password="YourSecurePassword";
```

---

## 7. Ağ ve Bağlantı Doğrulama Testleri (Windows PowerShell)

Uygulamayı başlatmadan önce Windows terminalinden K8s Kafka portuna erişilebildiğini doğrulayın:

```powershell
# 1. K8s Worker Node üzerindeki Kafka portuna TCP testi yapın:
Test-NetConnection -ComputerName 192.168.1.100 -Port 30092
```
*`TcpTestSucceeded : True`* yanıtını almalısınız.

---

## 8. Uygulamayı Başlatma ve Tüketim Logları

Uygulamayı derlenmiş Fat JAR ile başlatın:

```cmd
java -jar semantic-search-0.0.1-SNAPSHOT.jar
```

### Başarılı Bağlantı ve Dinleme Logları:
```text
INFO  --- [main] o.a.k.clients.consumer.ConsumerConfig    : ConsumerConfig values: group.id = semantic-search-indexer ...
INFO  --- [main] o.a.k.c.c.internals.ConsumerCoordinator : [Consumer clientId=..., groupId=semantic-search-indexer] Discovered group coordinator 192.168.1.100:30092
INFO  --- [main] o.a.k.c.c.internals.ConsumerCoordinator : [Consumer clientId=..., groupId=semantic-search-indexer] Subscribed to topic(s): olaylar-events
INFO  --- [main] o.a.k.c.c.internals.ConsumerCoordinator : [Consumer clientId=..., groupId=semantic-search-indexer] Successfully joined group with generation 1
INFO  --- [main] o.a.k.c.c.internals.ConsumerCoordinator : [Consumer clientId=..., groupId=semantic-search-indexer] Adding newly assigned partitions: olaylar-events-0
INFO  --- [search-indexer-0-C-1] c.e.s.service.SearchEventProcessor : Kafka olayı alındı: eventId=f81d4fae-..., type=OLAY, docId=olay-10524, version=1
INFO  --- [search-indexer-0-C-1] c.e.s.service.IndexingService      : Doküman OpenSearch'e başarıyla indekslendi: id=olay-10524, index=olaylar
```

---

## 9. Dağıtık Idempotency (Çift Kayıt ve Sırasız Veri Koruması - Veritabanı Gerektirmez)

Airgap ağdaki K8s üreticileri bazen ağ kesilmeleri nedeniyle aynı mesajı 2 kere basabilir (At-least-once) veya 3. sürüm, 2. sürümden önce gelebilir:

1. **Bellek İçi Durum Deposu (`ConcurrentHashMap`)**: Mesaj geldiğinde dökümanın sürüm bilgisi bellek içindeki thread-safe durum deposunda kontrol edilir (Harici veritabanına ihtiyaç duyulmaz).
2. **Sürüm Kontrolü**:
   * Gelen mesajın `version` değeri hafızadakinden **küçük veya eşitse** (`gelen <= mevcut`): Olay bayat (stale) kabul edilir; OpenSearch'e dokunulmaz ve Kafka'ya `ACK` verilip sessizce geçilir.
   * Gelen mesajın `version` değeri **büyükse** (`gelen > mevcut`): Doğrudan OpenSearch güncellenir ve yeni versiyon hafızaya işlenir.
3. **Offset Commit**: OpenSearch işlemi başarıyla tamamlandıktan sonra Kafka offset'i kaydedilir (`AckMode.RECORD`). Asla veri kaybı yaşanmaz.
4. **Tek Veri Deposu (Single Source of Truth)**: Tüm dokümanlar, metinler, filtre alanları ve embedding vektörleri yalnızca **OpenSearch** içerisinde saklanır.

---

## 10. Sık Karşılaşılan Kubernetes-Kafka Sorunları ve Çözümleri

### S1: `UnknownHostException: kafka-0.kafka-headless.default.svc.cluster.local`
* **Neden**: K8s Kafka pod'u dış istemciye küme içi DNS adını göndermiştir.
* **Çözüm**:
  1. K8s yöneticinize Kafka `advertised.listeners` ayarını dış IP / NodePort olacak şekilde yapılandırmasını söyleyin.
  2. Veya acil geçici çözüm olarak Windows `C:\Windows\System32\drivers\etc\hosts` (veya Linux `/etc/hosts`) dosyasına ilgili K8s Node IP'sini ve bu hostname'i yazın.

### S2: `org.apache.kafka.common.errors.TimeoutException: Topic ... not present in metadata after 10000 ms`
* **Neden**: K8s kümesinde dinlenmek istenen topic henüz yaratılmamıştır ve Kafka'da `auto.create.topics.enable=false` ayarlanmıştır.
* **Çözüm**: K8s içindeki Kafka container'ında veya yönetim panelinde topic'i manuel oluşturun:
  ```bash
  kafka-topics.sh --bootstrap-server localhost:9092 --create --topic <topic-adiniz> --partitions 3 --replication-factor 1
  ```

### S3: Farklı JSON Formatları (Düz Doküman vs Zarflı Mesaj)
* **Durum**: Sistemimiz hem zarflı mesajları (`{eventId, eventType, documentId, version, data}`) hem de doğrudan ham JSON dökümlerini (`{id, title, content}`) otomatik algılayıp işleyebilir. Format uyuşmazlığı durumunda mesaj DLQ'ya düşmez, otomatik olarak OpenSearch modeline eşlenir.

### S4: Tanımlanmamış Olay Tipleri (Fallback Mekanizması)
* **Durum**: `application.yml` içinde tanımlanmamış bir `eventType` geldiğinde sistem olayı yoksaymaz; otomatik olarak varsayılan indekse (`SEARCH_DEFAULT_INDEX`) UPSERT işlemi olarak OpenSearch'e yazar. Silme işlemleri için `eventType` içinde "DELETE" geçmesi yeterlidir.

