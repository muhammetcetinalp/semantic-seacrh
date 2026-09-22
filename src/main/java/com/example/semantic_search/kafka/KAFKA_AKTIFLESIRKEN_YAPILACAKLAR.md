# Kafka Aktifleşirken Yapılacaklar

Bu dosya, mevcut ortamdaki Kafka bağlantısı etkinleştirilmeden önce tamamlanacak
ayarları ve kontrolleri listeler. Kafka tüketicisi şu anda varsayılan olarak kapalıdır.

## 1. Kafka bağlantı bilgilerini alın

- Bootstrap server adreslerini belirleyin.
- Dinlenecek topic adlarını belirleyin.
- Consumer group adını belirleyin.
- Kafka güvenlik yöntemini öğrenin: PLAINTEXT, SSL, SASL/SSL veya SASL/PLAINTEXT.
- SASL kullanılıyorsa mekanizmayı öğrenin: SCRAM, PLAIN, Kerberos veya başka bir yöntem.
- Gerekliyse kullanıcı, parola, truststore, keystore ve sertifika bilgilerini alın.

Temel ortam değişkenleri:

```sh
export KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092
export KAFKA_GROUP_ID=semantic-search-indexer
export SEARCH_KAFKA_TOPICS=domain.entity.events
export SEARCH_KAFKA_DEAD_LETTER_TOPIC=search.indexing.errors
```

SASL/TLS kullanılıyorsa örnek ek ayarlar:

```sh
export SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL
export SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=SCRAM-SHA-512
export SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG='org.apache.kafka.common.security.scram.ScramLoginModule required username="..." password="...";'
```

Parolaları ve sertifika içeriklerini Git'e eklemeyin. Bunları çalışma ortamının secret
yönetiminden sağlayın.

## 2. Yetkileri hazırlayın

Uygulamanın Kafka hesabına şu yetkileri verin:

- Kaynak topic'leri okuma.
- `semantic-search-indexer` consumer group'unu kullanma.
- Hata topic'ine yazma.
- Gerekliyse topic metadata bilgisini okuma.

Kaynak topic'ler ve hata topic'i önceden oluşturulmalıdır. Uygulama üretim ortamında
topic oluşturma sorumluluğu almaz.

## 3. Gerçek event sözleşmesini inceleyin

Her dinlenecek event için aşağıdaki bilgileri kaydedin:

- Event tipi veya event adını gösteren alan.
- Belgenin benzersiz kimlik alanı.
- Eventin benzersiz kimliği.
- Sıralama veya sürüm alanı.
- Event zamanı.
- Verinin tam görüntü mü, kısmi güncelleme mi olduğu.
- Silme eventinin biçimi.
- Mesaj formatı: JSON, Avro veya Protobuf.
- Avro/Protobuf kullanılıyorsa Schema Registry adresi ve kimlik doğrulama yöntemi.

Mevcut örnek kod şu alanları bekler:

```json
{
  "eventId": "demo-1-updated-v2",
  "eventType": "ENTITY_UPDATED",
  "documentId": "demo-1",
  "version": 2,
  "data": {
    "title": "Radar Alpha",
    "searchText": "Long range radar surveillance unit"
  }
}
```

Gerçek event farklıysa `SearchEventMapper` için gerçek formata uygun bir implementasyon
yazın. Listener ve `IndexingService` değiştirilmeden kullanılabilir.

## 4. Event route'larını tanımlayın

`application.yml` içindeki `search.kafka.routes` bölümünü gerçek event tiplerine göre
değiştirin:

```yaml
search:
  kafka:
    routes:
      "[GERCEK_KAYIT_GUNCELLENDI]":
        operation: UPSERT
        index-name: entities
        document-type: entity
      "[GERCEK_KAYIT_SILINDI]":
        operation: DELETE
        index-name: entities
        document-type: entity
```

Her event tipi için şu kararları verin:

- Event OpenSearch'e eklenecek/güncellenecekse `UPSERT` kullanın.
- Belge silinecekse `DELETE` kullanın.
- Hangi OpenSearch index'ine yazılacağını belirleyin.
- OpenSearch belgesindeki `type` değerini belirleyin.
- İndekslenmeyecek event tiplerini route listesine eklemeyin.

## 5. Sıralama ve tekrar işleme kuralını belirleyin

Mevcut kod, aynı belge için sürekli artan sayısal `version` bekler. PostgreSQL'de son
işlenen sürüm saklanır; aynı veya daha düşük sürümlü eventler atlanır.

Gerçek eventte `version` yoksa Kafka açılmadan önce alternatif belirleyin:

- Kaynak sistemin kayıt sürümü veya sequence numarası.
- Güvenilir ve artan başka bir sıra alanı.
- Yalnızca tekrarları engellemek için benzersiz event kimliği tablosu.

Sadece tarih alanı kullanmak, aynı zaman değerine sahip eventler ve saat farkları
nedeniyle ayrıca değerlendirilmelidir.

## 6. Event alanlarını OpenSearch belgesine eşleyin

Şu alanların kaynağını belirleyin:

- `title`
- `searchText`
- `structuredFields`
- `metadata`

Embedding yalnızca `searchText` üzerinden üretilmelidir. Eventin gönderdiği index adı,
belge türü veya embedding değeri doğrudan kabul edilmemelidir.

Event yalnızca bir kayıt kimliği içeriyorsa, `SearchEventMapper` yerine veya mapper'ın
arkasında kaynak API/veritabanından güncel veriyi alan bir bileşen ekleyin. Bu çağrının
timeout ve retry davranışını ayrıca tanımlayın.

Başarılı UPSERT sonrasında OpenSearch'e gönderilen kanonik `_source` JSON'u PostgreSQL
`indexing_state.document_source` TEXT alanında da saklanır. JSON; embedding dizisini, metadata'yı ve kök seviyeye açılmış
`structuredFields` alanlarını içerir. `indexName` JSON'a eklenmez; ayrı sütunda tutulur.
DELETE sonrasında durum `DELETED` olur ve aktif `document_source` temizlenir.

## 7. Retry ve hata topic'i ayarlarını kesinleştirin

Ortamın ihtiyacına göre şu değerleri belirleyin:

```sh
export SEARCH_KAFKA_RETRY_ATTEMPTS=2
export SEARCH_KAFKA_RETRY_DELAY_MS=1000
export SEARCH_KAFKA_CONCURRENCY=1
```

- Geçersiz mesajlar tekrar denenmeden hata topic'ine gider.
- PostgreSQL veya OpenSearch gibi geçici hatalar ayarlanan sayıda tekrar denenir.
- Tüm denemeler başarısız olursa orijinal mesaj hata topic'ine gönderilir.
- Hata topic'ine gönderim başarısız olursa kaynak offset commit edilmez.
- Hata topic'indeki mesajların izlenmesi ve yeniden işlenmesi için operasyon sürecini
  belirleyin.

## 8. Üretim öncesi testleri tamamlayın

- Test ortamında gerçek güvenlik ayarlarıyla Kafka bağlantısını doğrulayın.
- Her seçilen event tipi için örnek mesaj çalıştırın.
- UPSERT sonucunun doğru OpenSearch index'ine yazıldığını kontrol edin.
- DELETE sonucunda belgenin kaldırıldığını kontrol edin.
- Tekrarlanan ve eski sürümlü eventlerin atlandığını kontrol edin.
- Geçersiz mesajın hata topic'ine gittiğini kontrol edin.
- Geçici OpenSearch kesintisinde retry yapıldığını kontrol edin.
- Uygulama yeniden başladığında consumer offset'ten devam ettiğini kontrol edin.
- `/actuator/metrics/search.kafka.events` metriğini gözlemleme sistemine ekleyin.

Projede Kafka testlerini çalıştırmak için:

```sh
./mvnw clean test
```

Bu testler geçici bir Kafka broker başlatır; geliştirme makinesinde kalıcı Kafka
kurulumu gerektirmez.

## 9. Kafka tüketicisini etkinleştirin

Yukarıdaki adımlar tamamlandıktan sonra:

```sh
export SEARCH_KAFKA_ENABLED=true
./mvnw spring-boot:run
```

Başlangıç loglarında consumer'ın doğru topic partition'larını aldığını kontrol edin.
Ardından kontrollü bir test eventi gönderip PostgreSQL `indexing_state`, OpenSearch belgesi,
Kafka consumer lag ve uygulama metriklerini birlikte doğrulayın.
