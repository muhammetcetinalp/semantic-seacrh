# 🚀 Semantic Search Lab — Sıfırdan Kurulum ve Mimari Rehberi

Bu proje; **Java 21**, **Spring Boot 4.x**, **OpenSearch 3.8.0**, **PostgreSQL 17 (Alpine)**, **Apache Kafka (KRaft)** ve **React Vite** arayüzü kullanan, iki aşamalı (Two-Stage Retrieval) kurumsal bir hibrit arama motorudur.

> [!NOTE]
> **Kurumsal Hibrit Arama & Docker:**  
> Projede BM25 kelime araması, 1024-boyutlu HNSW yoğun vektör araması, RRF (Reciprocal Rank Fusion) ve Cross-Encoder Reranker entegre edilmiştir.

---

## 📑 İçindekiler
1. [Sistem Gereksinimleri ve Desteklenen Platformlar](#-1-sistem-gereksinimleri-ve-desteklenen-platformlar)
2. [Sıfırdan (0'dan) Kurulum Kılavuzu: Temiz Ubuntu 24 Sunucu](#-2-sıfırdan-0dan-kurulum-kılavuzu-temiz-ubuntu-24-sunucu)
3. [Yapılandırma Kılavuzu: Host, Port ve Model Parametreleri](#-3-yapılandırma-kılavuzu-host-port-ve-model-parametreleri)
4. [Canlı Kafka Simülasyonu & Test Verilerini Yükleme](#-4-canlı-kafka-simülasyonu--test-verilerini-yükleme)
5. [Arama Mimarisi, RRF ve Aday Limiti Ayarları](#-5-arama-mimarisi-ve-algoritma-detayları)
6. [REST API Referansı](#-6-rest-api-referansı)
7. [Sık Karşılaşılan Sorunlar ve Çözümleri (Troubleshooting)](#-7-sık-karşılaşılan-sorunlar-ve-çözümleri-troubleshooting)

---

> 💡 **Derinlemesine Teknik Mimari Rehberleri:**  
> Projenin tüm iç işleyişi ve kurulum detayları 7 ayrı uzmanlaşmış rehberde detaylandırılmıştır:
> 1. 🔍 **[ARAMA_MIMARISI_README.md](ARAMA_MIMARISI_README.md)**: Kullanıcı sorgu yazdığında isteğin uçtan uca akışı, BM25, Dense Vector (BGE-M3), RRF Füzyonu ve Cross-Encoder Reranker yaşam döngüsü.
> 2. 🗄️ **[VERI_DEPOLAMA_MIMARISI_README.md](VERI_DEPOLAMA_MIMARISI_README.md)**: PostgreSQL (`indexing_state`, `search_query_log`) ve OpenSearch (`olaylar`, Turkish Analyzer, GeoPoint, 1024-dim HNSW) veri modelleri ve saklama formatları.
> 3. 🛰️ **[KAFKA_VE_OLAY_AKISI_README.md](KAFKA_VE_OLAY_AKISI_README.md)**: Apache Kafka KRaft altyapısı, dağıtık Idempotency (çift kayıt önleme), `SELECT FOR UPDATE` kilitlemesi, sürüm takibi ve canlı simülasyon producer motoru.
> 4. 🐘 **[POSTGRES_KURULUMU_README.md](POSTGRES_KURULUMU_README.md)**: PostgreSQL 17 Airgap (kapalı ağ) kurulumu, uzak sunucu yapılandırması, `.env` ayarları, bootstrap SQL scriptleri ve sorun giderme kılavuzu.
> 5. 🔎 **[OPENSEARCH_KURULUMU_README.md](OPENSEARCH_KURULUMU_README.md)**: OpenSearch 3.8.0 Airgap (kapalı ağ) ve Windows kurulumu, yerel/uzak sunucu yapılandırması, k-NN vektör ayarları, `.env` entegrasyonu ve sorun giderme kılavuzu.
> 6. 🛰️ **[KAFKA_KUBERNETES_BAGLANTI_README.md](KAFKA_KUBERNETES_BAGLANTI_README.md)**: Kubernetes üzerinde koşan Kafka kümesine Airgap ortamdan bağlanma, NodePort/LoadBalancer ayarları, topic dinleme, olay sözleşmesi (JSON schema) ve Idempotency kılavuzu.
> 7. 🧠 **[EMBEDDING_VE_RERANKER_KURULUMU_README.md](EMBEDDING_VE_RERANKER_KURULUMU_README.md)**: Airgap ortamdaki uzak model sunucusuna (BGE-M3 & Cross-Encoder) bağlanma, `.cer` SSL sertifikası, API Key kimlik doğrulama ve RestClient yapılandırma kılavuzu.
> 8. 🧭 **[YENI_VERI_MODELI_ENTEGRASYON_REHBERI.md](YENI_VERI_MODELI_ENTEGRASYON_REHBERI.md)**: Airgap ortamda demo "Olaylar" yerine tamamen farklı bir operasyonel veri türü geldiğinde PostgreSQL DDL, Kafka Consumer, OpenSearch 3.8.0 Mapping/Search/Filter ve BGE-M3 Embedding katmanlarının uçtan uca uyarlanması kılavuzu.

---

## 💻 1. Sistem Gereksinimleri ve Desteklenen Platformlar

Bu proje modern kurumsal sunucu ve geliştirme ortamlarında çalışacak şekilde tasarlanmıştır:
* **İşletim Sistemi:** Ubuntu 24.04 LTS (Noble Numbat), Ubuntu 22.04 LTS, Debian 12 veya macOS.
* **Donanım / Mimari:** x86_64, aarch64 (ARM64) veya SPARC/Server platformları.
* **Minimum Kaynak:** 4 vCPU, 8 GB RAM (Önerilen: 16 GB RAM), 25 GB Disk Alanı.

---

## 🛠️ 2. Sıfırdan (0'dan) Kurulum Kılavuzu: Temiz Ubuntu 24 Sunucu

> [!IMPORTANT]
> **Hiçbir Şey Kurulu Olmayan Sıfır Ubuntu 24 Cihazı:**  
> Makinenizde **Java, Docker veya Node.js dahil hiçbir şey kurulu olmasa bile**, aşağıdaki adımları terminalde sırasıyla çalıştırarak sistemi 10 dakikada sıfırdan ayağa kaldırabilirsiniz.

---

### Adım 2.1: Temel Sistem Paketleri ve Ağ Araçlarını Kurun

Temiz Ubuntu 24 kurulumunun paket listelerini tazeleyip temel derleme ve ağ yardımcılarını yükleyin:

```bash
# 1. Paket listelerini güncelleyin ve yükseltin:
sudo apt update && sudo apt upgrade -y

# 2. Temel araçları yükleyin:
sudo apt install -y curl wget git net-tools ca-certificates gnupg lsb-release apt-transport-https htop ufw
```

---

### Adım 2.2: Java 21 (OpenJDK 21) Kurulumu

Proje Spring Boot 4.x ve Java 21 LTS gerektirir. Ubuntu 24 resmi depolarında OpenJDK 21 varsayılan olarak mevcuttur:

```bash
# 1. Java 21 JDK paketini kurun:
sudo apt install -y openjdk-21-jdk

# 2. Kurulumu doğrulayın:
java -version
javac -version

# 3. JAVA_HOME çevre değişkenini bash profilinize ekleyin:
echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-$(dpkg --print-architecture)' >> ~/.bashrc
source ~/.bashrc
```

---

### Adım 2.3: Docker Engine ve Docker Compose (Plugin) Kurulumu

PostgreSQL, OpenSearch ve Kafka konteynerlerini çalıştırmak için Docker Engine ve `compose` eklentisi kurulmalıdır:

#### Yöntem A: Resmi Docker Depolarından Kurulum (Önerilen)
```bash
# 1. Resmi Docker GPG anahtarını ekleyin:
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

# 2. Docker Ubuntu 24 (noble) repository'sini ekleyin:
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 3. Docker paketlerini kurun:
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 4. Mevcut kullanıcınızı docker grubuna ekleyin (sudo yazmadan çalıştırmak için):
sudo usermod -aG docker $USER
newgrp docker
```

---

### Adım 2.4: Node.js 20 LTS ve NPM Kurulumu (Frontend için)

React Vite arayüzünü derlemek ve yerel web sunucusunu çalıştırmak için Node.js 20+ gereklidir:

```bash
# 1. NodeSource resmi Node.js 20 deposunu ekleyin:
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -

# 2. Node.js paketini kurun:
sudo apt install -y nodejs

# 3. Kurulum sürümlerini kontrol edin:
node -v   # v20.x.x
npm -v    # 10.x.x
```

---

### Adım 2.5: Linux Bellek ve Dosya Tanımlayıcı (OpenSearch Limitleri)

OpenSearch'ün `mmapfs` ve bellek eşleme hataları vermemesi için çekirdek parametrelerini yapılandırın:

```bash
# 1. Geçici olarak vm.max_map_count değerini 262144 yapın:
sudo sysctl -w vm.max_map_count=262144

# 2. Kalıcı olması için sysctl yapılandırmasına ekleyin:
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf

# 3. Dosya tanımlayıcı sınırlarını artırın:
echo "* soft nofile 65536" | sudo tee -a /etc/security/limits.conf
echo "* hard nofile 65536" | sudo tee -a /etc/security/limits.conf
```

---

### Adım 2.6: Embedding ve Dil Modeli Bağlantısı (BGE-M3 / API Key / SSL)

#### Seçenek A: Harici Model Sunucusu (API Key & SSL .cer)
Uzak sunucudaki model API adresini ve anahtarınızı `.env` dosyasına girin:
```properties
EMBEDDING_PROVIDER=rest
EMBEDDING_ENDPOINT=https://model-server.airgap.lan/v1/embeddings
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_DIMENSIONS=1024
AI_API_KEY=your_api_key
```

#### Seçenek B: Model Sunucusu Olmadan Hızlı Test için MOCK Modu
> [!TIP]
> Model sunucusu olmadan tüm sistemi test etmek için `.env` dosyasında `EMBEDDING_PROVIDER=mock` yapmanız yeterlidir!

---

### Adım 2.7: Projeyi Klonlayın ve `.env` Dosyasını Oluşturun

```bash
# 1. Depoyu klonlayın ve klasöre girin:
git clone <repo-url> semantic-search
cd semantic-search

# 2. Maven wrapper dosyasına çalıştırma izni verin:
chmod +x ./mvnw

# 3. Ortam değişkenleri şablonunu kopyalayın:
cp .env.example .env
```

---

### Adım 2.8: Docker Altyapısını Başlatın (OpenSearch, PostgreSQL, Kafka)

Sistemin ihtiyaç duyduğu 3 temel konteyner tek bir komutla (`docker compose up -d`) ayağa kalkar:

```bash
docker compose up -d
```

Bu komut şu 3 servisi arka planda başlatır:
1. **OpenSearch 3.8.0** (`port: 9200`): BM25 metin indeksi, Türkçe analizör, 1024-dim k-NN vektör alanı ve Geo-Point koordinatları.
2. **PostgreSQL 17** (`port: 5432`): İndeksleme durum takibi (`indexing_state`), loglar ve denetim kayıtları.
3. **Apache Kafka 3.7.0 (KRaft)** (`port: 9092`): Zookeeper gerektirmeyen, hafif ve yüksek performanslı olay akış kuyruğu.

#### Konteyner Bağlantı Testleri:
```bash
# 1. OpenSearch Testi (3.8.0):
curl -s http://localhost:9200 | grep number

# 2. PostgreSQL DB Port Testi:
nc -zvw3 localhost 5432

# 3. Kafka Broker Port Testi:
nc -zvw3 localhost 9092
```

---

### Adım 2.9: Spring Boot Backend'i Başlatın

Proje kök dizininde Maven wrapper ile backend'i çalıştırın:

```bash
./mvnw spring-boot:run
```

**Başarılı Açılış Logları:**
* Flyway şemaları otomatik uygulanır: `Successfully applied migration to schema "SEMANTIC_SEARCH", now at version v5`
* Tomcat `8080` portunda açılır: `Started SemanticSearchApplication in ~2.5 seconds`

Sağlık kontrolü:
```bash
curl -s http://localhost:8080/actuator/health
# Çıktı: {"groups":["liveness","readiness"],"status":"UP"}
```

---

### Adım 2.10: Modern Web Arayüzünü Başlatın

Yeni bir terminal sekmesinde (veya `screen` / `tmux` oturumunda) `frontend` klasörüne gidin:

```bash
cd frontend
npm install
npm run dev -- --host 0.0.0.0
```

* **Lokal Erişim:** Tarayıcınızda **[http://localhost:5173](http://localhost:5173)** adresini açın.
* **Uzak Sunucu Erişimi:** Kendi bilgisayarınızdan **`http://<sunucu-ip-adresi>:5173`** adresine bağlanabilirsiniz!

---

### Adım 2.11: Ubuntu Güvenlik Duvarı (UFW) Ayarları (Uzak Erişim için)

Eğer Ubuntu sunucunuzda UFW (Uncomplicated Firewall) aktifse, gerekli portlara izin verin:

```bash
sudo ufw allow 8080/tcp  # Spring Boot REST API
sudo ufw allow 5173/tcp  # React Vite Frontend Arayüzü
sudo ufw allow 9200/tcp  # OpenSearch (İsteğe bağlı)
sudo ufw allow 5432/tcp  # PostgreSQL DB (İsteğe bağlı)
sudo ufw allow 9092/tcp  # Kafka (İsteğe bağlı)
sudo ufw status
```

---

## ⚙️ 3. Yapılandırma Kılavuzu: Host, Port ve Model Parametreleri

Tüm yapılandırmalar **`.env`** dosyası ve **`src/main/resources/application.yml`** üzerinden yönetilir. Hangi parametrenin ne işe yaradığı, nereden ve neye göre ayarlanacağı aşağıda detaylandırılmıştır:

### 3.1. Veritabanı ve Host/Port Ayarları

| Değişken Adı | Varsayılan Değer | Neye Göre Değiştirilmeli? | Açıklama |
| :--- | :--- | :--- | :--- |
| `DB_HOST` / `DB_PORT` | `localhost` / `5432` | PostgreSQL farklı bir sunucuda veya bulutta ise | PostgreSQL veritabanı bağlantı adresi |
| `DB_NAME` | `semantic_search` | Veritabanı adı | PostgreSQL veritabanı adı |
| `DB_USERNAME` | `semantic_search` | Veritabanı kullanıcı adı | Uygulama şema kullanıcısı |
| `DB_PASSWORD` | `SearchDev2026` | Üretim ortamında güçlü parola | Kullanıcı şifresi |
| `OPENSEARCH_HOST` | `localhost` | AWS OpenSearch / harici cluster adresi | OpenSearch IP veya Domain adresi |
| `OPENSEARCH_PORT` | `9200` | OpenSearch portu | HTTP arama portu |
| `SERVER_PORT` | `8080` | Port çakışması durumunda | Spring Boot API portu |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Frontend farklı bir domainde barındırılıyorsa | İzin verilen web arayüz adresleri |

---

### 3.2. Model ve Embedding Parametreleri

| Değişken Adı | Varsayılan | Seçenekler | Neye Göre Ayarlanmalı? |
| :--- | :--- | :--- | :--- |
| `EMBEDDING_PROVIDER` | `rest` | `rest`, `mock`, `tei` | Uzak REST model API'si için `rest`, TEI için `tei`, modelsiz test için `mock`. |
| `EMBEDDING_MODEL` | `BAAI/bge-m3` | Model adı | Embedding model adı. |
| `EMBEDDING_DIMENSIONS` | `1024` | `384`, `768`, `1024`, `1536` | **ÇOK ÖNEMLİ:** Kullandığınız modelin çıktı boyutudur. *BGE-M3 için 1024.* OpenSearch indeksi bu boyuta göre oluşturulur. |
| `EMBEDDING_ENDPOINT` | `https://model-server.airgap.lan/v1/embeddings` | URL | Embedding sağlayıcısının REST adresi. |

---

### 3.3. Cross-Encoder Reranker Parametreleri

| Değişken Adı | Varsayılan | Açıklama |
| :--- | :--- | :--- |
| `SEARCH_RERANKING_ENABLED` | `true` | Reranker aşamasının açık/kapalı durumu. |
| `SEARCH_RERANKING_PROVIDER`| `java` | `java`: Saf Java Cross-Encoder. `tei`: TEI / REST reranker servisi. |
| `SEARCH_RERANKING_MODEL` | `BAAI/bge-reranker-v2-m3` | Reranker model referans adı. |

---

### 3.4. Apache Kafka ve Canlı Olay Akışı Parametreleri

`docker compose up -d` komutuyla ayağa kalkan KRaft modundaki Apache Kafka broker'ı üzerinden asenkron indeksleme ve simülasyon ayarları:

| Değişken Adı | Varsayılan Değer | Neye Göre Değiştirilmeli? | Açıklama |
| :--- | :--- | :--- | :--- |
| `SEARCH_KAFKA_ENABLED` | `true` | Kafka tüketimini devre dışı bırakmak için `false` yapılabilir | Spring Boot Kafka listener'ını aktif eder. |
| `KAFKA_BOOTSTRAP_SERVERS`| `localhost:9092` | Harici veya bulut Kafka kümesi adresi | Kafka broker bağlantı adresi. |
| `SEARCH_KAFKA_TOPICS` | `olaylar-events` | Dinlenecek kaynak topic adı | Olayların aktığı Kafka topic'i. |
| `KAFKA_GROUP_ID` | `semantic-search-indexer` | Consumer group adı | Tüketici grup kimliği. |
| `KAFKA_PORT` | `9092` | Port çakışması durumunda | Docker host portu. |

---

### 3.5. Arama, Aday ve RRF Limit Parametreleri

Arama isteklerinde varsayılan olarak kaç döküman taranacağı ve kullanıcıya kaç sonuç listeleneceğini belirleyen değişkenler:

| Değişken Adı | Varsayılan Değer | Neye Göre Değiştirilmeli? | Açıklama |
| :--- | :--- | :--- | :--- |
| `SEARCH_DEFAULT_LIMIT` | `20` | UI veya API'den limit verilmediğinde | Varsayılan olarak dönecek sonuç adedi. |
| `SEARCH_MAX_LIMIT` | `100` | Aşırı bellek/ağ kullanımını engellemek için | Bir sorguda istenebilecek azami aday/sonuç üst tavanı. |
| `SEARCH_DEFAULT_INDEX` | `olaylar` | Hedef indeks değiştirilmek istendiğinde | OpenSearch'te varsayılan sorgulanan indeks adı. |

---

## 📥 4. Canlı Kafka Simülasyonu & Test Verilerini Yükleme

Projede, doğrudan veritabanına statik toplu yükleme yapmak yerine, gerçek dünyadaki kurumsal mimarilere uygun olarak **10.000 adet gerçek askeri/güvenlik olay raporunu (`olaylar.json`)** canlı olarak Kafka üzerinden akıtan ve eş zamanlı indeksleyen bir simülasyon altyapısı kurulmuştur.

### 4.1. Canlı Akış Pipeline'ı Nasıl Çalışır?

```
[ olaylar.json (10.000 Olay) ]
             │
             ▼
[ OlaylarKafkaSimulationProducer (Java Producer) ]
             │ (Ayarlanabilir Hız: 20 olay/sn)
             ▼
[ Apache Kafka (Topic: olaylar-events) ]
             │
             ▼
[ KafkaIndexingListener (Spring Boot Consumer) ]
             │
             ├───────────────────────────────┐
             ▼ (1. Durum Takibi & Audit)     ▼ (2. BM25, k-NN & Geo-Point)
     [ PostgreSQL 17 DB ]             [ OpenSearch 3.8.0 ]
     indexing_state tablosu          olaylar indeksi
     (Idempotency & Versiyon)        (turkish_search, 1024-dim Vector & GeoPoint)
```

Kafka'dan tüketilen her bir olay mesajı eş zamanlı olarak:
1. **PostgreSQL (`indexing_state`)**: Idempotency ve versiyon kontrolüyle kaydedilir; işlem durumu `INDEXED` yapılır.
2. **OpenSearch (`olaylar`)**: Başlık, özet (`shortText`), tam rapor (`longText`), adres, birim, tarih, GPS koordinatları (`geo_point`) ve 1024-boyutlu HNSW vektörü ile indeksine yazılır.

---

### 4.2. Simülasyonu Çalıştırma Yöntemleri

Simülasyonu dilediğiniz yöntemle başlatabilirsiniz:

#### Seçenek A: Web Arayüzünden Canlı İzleyerek (Önerilen)
1. Tarayıcınızda **[http://localhost:5173](http://localhost:5173)** adresini açın.
2. Sol paneldeki **"🛰️ Canlı Kafka Akış Simülasyonu"** bileşenine gelin.
3. **Adet** (örn: *1.000 Olay*) ve **Akış Hızı** (örn: *20 olay/sn*) seçin.
4. **"▶ Canlı Akışı Başlat"** butonuna tıklayın:
   - Kafka'ya gönderilen ve PostgreSQL/OpenSearch'e işlenen olay sayaçlarının canlı aktığını görebilirsiniz.
   - Akış devam ederken yukarıdaki arama kutusundan sorgu yaptığınızda yeni gelen olayların anında sonuçlara dahil olduğunu test edebilirsiniz.

#### Seçenek B: Terminalden Bağımsız Java Scripti Olarak (CLI)
Spring Boot'tan bağımsız olarak producer'ı bir CLI scripti gibi terminalden de çalıştırabilirsiniz:
```bash
./mvnw test-compile exec:java \
  -Dexec.mainClass="com.example.semantic_search.simulation.OlaylarKafkaSimulationProducer" \
  -Dexec.args="--limit 500 --delay 50"
```
*(Parametreler: `--limit <adet>` `--delay <milisaniye>` `--topic <topic-adi>` `--servers <host:port>`)*

#### Seçenek C: REST API Üzerinden
```bash
# 1.000 olayı 50ms gecikmeyle (20 olay/sn) başlat:
curl -X POST "http://localhost:8080/api/v1/simulation/kafka/start?limit=1000&delayMs=50"

# Canlı durumu ve sayaçları sorgula:
curl -s http://localhost:8080/api/v1/simulation/kafka/status

# Simülasyonu durdur:
curl -X POST "http://localhost:8080/api/v1/simulation/kafka/stop"
```

---

### 4.3. Örnek Taktik Arama Sorguları ile Doğrulama

Veriler akarken veya tamamlandıktan sonra arayüzde aşağıdaki sorguları test edebilirsiniz:

1. **`İstanbul boğazı şüpheli gemi takibi`**
   - ➡️ Kuzey girişi ve açık denizdeki gemi takibi olaylarını hem BM25 hem Semantik arama ile en üst sırada yakalar.
2. **`Karadeniz devriye fırkateyn tatbikatı`**
   - ➡️ Amfibi ve muhrip unsurların Karadeniz sahasındaki harekat raporlarını listeler.
3. **`Ege denizi düzensiz göçmen kurtarma`**
   - ➡️ Sahil Güvenlik Komutanlığı'nın arama kurtarma faaliyetlerini filtrelerle birlikte sunar.
4. **Filtreleri Kullanarak Daraltma**:
   - Sol menüden **Görevli Birim** (örn: *"Hücumbot Filotilla Komutanlığı"* veya *"Sahil Güvenlik Komutanlığı"*) ve **Olay Tarihi Aralığı** seçerek sonuçları hassas şekilde sınırlandırabilirsiniz.

---

## 🏗️ 5. Arama Mimarisi ve Algoritma Detayları

```
[ Kullanıcı Sorgusu (Query) ]
            │
            ├───────────────────────────────┐
            ▼ (1. Aşama: Kelime Bazlı)      ▼ (1. Aşama: Dense Semantik)
      [ OpenSearch BM25 ]             [ BGE-M3 Dense Vektör ]
      Tam kelime sıklığı &            1024-dim HNSW kosinüs
      TF-IDF terim eşleşmesi          semantik tema benzerliği
            │                               │
            └───────────────┬───────────────┘
                            │
                            ▼
              [ RRF (Reciprocal Rank Fusion) ]
              RRF(d) = Σ w_i / (k + rank_i(d))
                            │
                            ▼
        [ 2. Aşama: Cross-Encoder Reranker ]
        Top adayları derin bağlamsal çapraz analizle yeniden puanlar
                            │
                            ▼
                [ Kullanıcıya Nihai Liste ]
```

### 1. Reciprocal Rank Fusion (RRF)
Farklı arama motorlarının skor skalaları birbiriyle uyumsuzdur (BM25 skoru `15.4` iken kosinüs skoru `0.82` olabilir). RRF mutlak puanları değil sıralamaları birleştirir:
$$RRF(d) = \frac{w_{BM25}}{k + rank_{BM25}(d)} + \frac{w_{SEM}}{k + rank_{SEM}(d)}$$
*(Varsayılan $k=60$ parametresi düşük sıralardaki gürültüyü baskılar).*

### 2. Arama & RRF Parametreleri: Aday Sayısı ve Sonuç Limiti Nasıl Ayarlanır?

Sistem iki aşamalı (Two-Stage Retrieval) mimariye sahip olduğu için iki temel limit kavramı vardır:

1. **Aday Sayısı (`candidateLimit`)**: BM25 ve Vektör motorlarından **ayrı ayrı kaçar aday döküman çekileceğini** belirler.
2. **Nihai Sonuç Limiti (`limit`)**: RRF birleştirmesinden ve Cross-Encoder Reranker'dan sonra **kullanıcıya nihai olarak kaç döküman döneceğini** belirler.

#### 🧮 Aday Sayısı Hesaplama Mantığı:
$$\text{candidateLimit} = \min(\text{limit} \times \text{candidateMultiplier}, \text{maxLimit})$$

* **Örnek Senaryo:**
  - Kullanıcı `limit = 10` ve `candidateMultiplier = 3` (3×) seçtiğinde:
  - **OpenSearch (BM25)** ilk **30 adayı** çeker.
  - **OpenSearch (Dense Vektör)** ilk **30 adayı** çeker.
  - RRF algoritması bu iki havuzdaki dökümanları sıralama puanlarına göre harmanlar.
  - En yüksek RRF skoruna sahip ilk **10 döküman** (`limit`) Cross-Encoder aşamasına sunulur.

---

#### 🎛️ Bu Parametreler Nereden Değiştirilir?

Bu parametrik ayarları ihtiyacınıza göre 3 farklı seviyeden değiştirebilirsiniz:

##### 1. Web Arayüzünden (Canlı / İnteraktif)
Tarayıcıda (`http://localhost:5173`) sol kenardaki kontrol panelinde yer alan kontrolleri kullanarak sayfayı yenilemeden anlık test edebilirsiniz:
* **"Sonuç Limiti" Slider'ı (`limit`)**: Kullanıcıya gösterilecek nihai döküman sayısını belirler (1 ile 100 arası).
* **"Aday Çarpanı" Slider'ı (`candidateMultiplier`)**: BM25 ve Vektör motorlarının toplayacağı aday havuzunun büyüklüğünü belirler (1× ile 10× arası).
* **"Rank Sabiti (k)" Slider'ı (`rankConstant`)**: RRF formülündeki $k$ sabitini belirler (1 ile 1000 arası, varsayılan: 60).
* **"Hibrit Ağırlıklar" Slider'ı**: BM25 ve Semantik motorların RRF'e yüzde kaç etki edeceğini belirler (örn: %50-%50 veya %70-%30).

##### 2. REST API İsteğinden (Sorgu Bazlı / Programatik)
`POST /api/v1/search/explain` veya `POST /api/v1/search` çağrılarında istek gövdesinde (JSON) doğrudan belirtebilirsiniz:
```json
{
  "query": "arama kurtarma faaliyeti",
  "limit": 15,
  "candidateMultiplier": 4,
  "rankConstant": 60,
  "bm25Weight": 0.6,
  "semanticWeight": 0.4
}
```
*(Bu istekte `candidateLimit = 15 * 4 = 60` aday taranır ve en iyi `15` sonuç RRF ile döner).*

##### 3. Yapılandırma Dosyalarından (Sistem Geneli Varsayılanlar)
Uygulama genelindeki varsayılan limitleri ve güvenlik tavanını `.env` veya `application.yml` dosyasından değiştirebilirsiniz:

* **`.env` Dosyası:**
  ```properties
  SEARCH_DEFAULT_LIMIT=20      # İstemci limit belirtmezse varsayılan dönecek adet
  SEARCH_MAX_LIMIT=100         # Bir sorguda istenebilecek maksimum güvenlik tavanı
  SEARCH_DEFAULT_INDEX=olaylar # Varsayılan OpenSearch indeksi
  ```

* **`src/main/resources/application.yml` Dosyası:**
  ```yaml
  search:
    defaults:
      limit: ${SEARCH_DEFAULT_LIMIT:20}
      max-limit: ${SEARCH_MAX_LIMIT:100}
      default-search-type: HYBRID
      default-index-name: ${SEARCH_DEFAULT_INDEX:olaylar}
  ```

---

## 📡 6. REST API Referansı

| Metot | Endpoint | İstek Gövdesi / Parametre | Açıklama |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/search` | `{"query": "...", "searchType": "HYBRID"}` | Basit arama listesi döner. |
| `POST` | `/api/v1/search/explain` | `HybridExplainRequest` | UI için BM25, Semantik, RRF ve Reranker detaylı karşılaştırması döner. |
| `POST` | `/api/v1/simulation/kafka/start` | `limit=1000&delayMs=50` | Canlı Kafka olay akışı simülasyonunu başlatır. |
| `POST` | `/api/v1/simulation/kafka/stop` | - | Çalışan simülasyonu durdurur. |
| `GET` | `/api/v1/simulation/kafka/status` | - | Kafka yayınlanan ve PostgreSQL işlenen canlı sayaçları döner. |
| `GET` | `/api/v1/olaylar/meta` | - | Dosyadaki toplam olay, birim ve tür istatistiklerini döner. |
| `POST` | `/api/v1/index` | `IndexDocumentRequest` | Tekil döküman ekler/günceller (OpenSearch + PostgreSQL). |
| `POST` | `/api/v1/index/bulk` | `List<IndexDocumentRequest>` | Toplu döküman indeksler. |
| `GET` | `/actuator/health` | - | Spring Boot sistem ve bileşen sağlık durumu. |

---

## 🔧 7. Sık Karşılaşılan Sorunlar ve Çözümleri (Troubleshooting)

### S1: `docker compose up` sonrası Spring Boot veritabanı bağlantı hatası veriyor
* **Neden:** PostgreSQL konteyneri başlatılırken kısa bir süre hazır olma aşamasından geçebilir.
* **Çözüm:** `docker compose ps` komutunu çalıştırıp `postgres` servisinin durumu `(healthy)` olana kadar bekleyin, ardından Spring Boot'u başlatın.

### S2: Port 8080 veya 5173 kullanımda hatası
* **Çözüm:**
  - Backend portunu değiştirmek için: `.env` dosyasında `SERVER_PORT=8081` yapın.
  - Frontend'in yeni backend portuna bağlanabilmesi için: `frontend/.env` dosyasına `VITE_BACKEND_PROXY_TARGET=http://localhost:8081` yazın.
  - Frontend portunu değiştirmek için: `frontend/vite.config.ts` içinde `server.port` değerini düzenleyin.
  - Değişiklik sonrası backend `.env` içerisindeki `CORS_ALLOWED_ORIGINS` satırına yeni portu eklemeyi unutmayın.

---

## 🛑 Servisleri Kapatma ve Temizleme

Verileri kaybetmeden konteynerleri durdurmak için:
```bash
docker compose stop
```

Konteynerleri, ağları ve **bütün veritabanı volume'lerini tamamen silip sıfırlamak için**:
```bash
docker compose down -v
```
