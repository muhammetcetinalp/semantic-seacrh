# 🔎 OpenSearch 3.8.0 Kurulum, Yapılandırma ve Airgap (Windows) Kılavuzu

Bu kılavuz; **kapalı devre (airgap / internetsiz) kurumsal ağda**, Windows işletim sistemi üzerinde **OpenSearch 3.8.0** motorunun kurulumu, bellek ve k-NN (vektör arama) ayarlarının yapılması, ilk aşamada yerel makinede (`localhost`), ardından uzak sunucuda çalıştırılması ve Spring Boot hibrit arama uygulamasıyla entegrasyonu için gereken tüm adımları içermektedir.

---

## 📑 İçindekiler
1. [Genel Mimari ve Kurulum Aşamaları](#1-genel-mimari-ve-kurulum-aşamaları)
2. [Ön Hazırlık: Paket ve Windows Sistem Ayarları](#2-ön-hazırlık-paket-ve-windows-sistem-ayarları)
3. [Arşivi Çıkarma ve Dizin Kuralları](#3-arşivi-çıkarma-ve-dizin-kuralları)
4. [OpenSearch Yapılandırması (`config/opensearch.yml`)](#4-opensearch-yapılandırması-configopensearchyml)
5. [JVM ve Bellek Yapılandırması (`config/jvm.options`)](#5-jvm-ve-bellek-yapılandırması-configjvmoptions)
6. [OpenSearch'ü Başlatma (`bin\opensearch.bat`)](#6-opensearchü-başlatma-binopensearchbat)
7. [Kurulumu Doğrulama ve Fonksiyonel Testler](#7-kurulumu-doğrulama-ve-fonksiyonel-testler)
8. [Spring Boot Uygulaması ve `.env` Entegrasyonu](#8-spring-boot-uygulaması-ve-env-entegrasyonu)
9. [2. Aşamaya Geçiş: Uzak Sunucuya Taşıma Adımları](#9-2-aşamaya-geçiş-uzak-sunucuya-taşıma-adımları)
10. [Windows Servisi Olarak Arka Planda Çalıştırma (Opsiyonel)](#10-windows-servisi-olarak-arka-planda-çalıştırma-opsiyonel)
11. [Sık Karşılaşılan Sorunlar ve Çözümleri](#11-sık-karşılaşılan-sorunlar-ve-çözümleri)

---

## 1. Genel Mimari ve Kurulum Aşamaları

Projemiz, hibrit arama mimarisinde iki temel işlevi OpenSearch üzerinden yürütür:
* **BM25 Lexical Arama**: Türkçe morfolojik analizör (`turkish_search`), stopwords ve stemmer ile kelime bazlı arama.
* **Dense Vector k-NN Arama**: 1024 boyutlu (BGE-M3) embedding vektörleri üzerinde **HNSW** algoritması ve **FAISS** motoru ile kosinüs benzerliği (`cosinesimil`) araması.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ AŞAMA 1: Windows İstemci Makinede Yerel Çalışma                             │
│                                                                             │
│  ┌──────────────────────────────┐          HTTP :9200                       │
│  │ Semantic Search Spring Boot  │ ────────────────────────>  OpenSearch     │
│  │ (.env: OPENSEARCH_HOST=localhost)                        3.8.0 (Yerel)   │
│  └──────────────────────────────┘                                           │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ AŞAMA 2: İleri Aşamada Uzak Airgap Sunucusuna Taşıma                        │
│                                                                             │
│  ┌──────────────────────────────┐     LAN TCP :9200        ┌──────────────┐ │
│  │ Semantic Search Spring Boot  │ ───────────────────────> │  OpenSearch  │ │
│  │ (.env: OPENSEARCH_HOST=192.168.1.60)                    │ 3.8.0 Sunucu │ │
│  └──────────────────────────────┘                          └──────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Ön Hazırlık: Paket ve Windows Sistem Ayarları

### 2.1. İndirilecek Paket ve Resmi İndirme Siteleri
Kullanılacak kesin sürüm: **OpenSearch 3.8.0 (Windows x64)**

Paketi indirebileceğiniz resmi adresler:
* 🌐 **Resmi İndirme Portalı**: [https://opensearch.org/downloads.html](https://opensearch.org/downloads.html)
* 📦 **Tüm Sürümler / Arşiv Sayfası**: [https://opensearch.org/downloads.html#artifacts](https://opensearch.org/downloads.html#artifacts)
* 🐙 **GitHub Resmi Deposu**: [https://github.com/opensearch-project/OpenSearch](https://github.com/opensearch-project/OpenSearch)
* 🚀 **Doğrudan İndirme CDN Linki (Windows x64 Zip)**:  
  `https://artifacts.opensearch.org/releases/bundle/opensearch/3.8.0/opensearch-3.8.0-windows-x64.zip`

* **Paket Avantajları**:
  - ✅ **Dahili Java (Bundled OpenJDK 21)**: Paketin içinde `jdk/` klasörü hazır gelir. Windows'ta Java kurulu olmasa bile çalışır.
  - ✅ **Dahili k-NN Eklentisi**: Vektör arama için gereken `opensearch-knn` eklentisi ve FAISS kütüphaneleri zip içinde hazır gelir.

### 2.2. Windows Uzun Dosya Yolu Desteğini Açma (Long Paths)
Windows varsayılan 260 karakterlik yol sınırından dolayı zip arşivini açarken hata almamak için **Yönetici olarak açılmış PowerShell**'de şu komutu çalıştırın:

```powershell
Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" -Name "LongPathsEnabled" -Type DWORD -Value 1 -Force
```

---

## 3. Arşivi Çıkarma ve Dizin Kuralları

> [!CRITICAL]
> **DİZİN YOLUNDA ASLA BOŞLUK VEYA TÜRKÇE KARAKTER OLMAMALIDIR!**  
> `C:\Program Files\OpenSearch` gibi aralarında boşluk olan yollara açarsanız OpenSearch Java batch scriptleri başlatılamaz (`Could not find or load main class`).

### Önerilen Çıkarma Yolu:
1. `opensearch-3.8.0-windows-x64.zip` dosyasını `C:\` kök dizinine taşıyın.
2. Sağ tıklayıp **Buraya Ayıkla** (Extract All) deyin veya PowerShell ile açın:
   ```powershell
   Expand-Archive -Path "C:\opensearch-3.8.0-windows-x64.zip" -DestinationPath "C:\opensearch-3.8.0"
   ```
3. Hedef dizin yapınız şöyle görünmelidir:
   ```text
   C:\opensearch-3.8.0\
   ├── bin\              (opensearch.bat, opensearch-plugin.bat vb.)
   ├── config\           (opensearch.yml, jvm.options)
   ├── jdk\              (Dahili OpenJDK runtime)
   ├── lib\              (OpenSearch çekirdek kütüphaneleri)
   ├── plugins\          (opensearch-knn, neural-search vb.)
   └── modules\
   ```

---

## 4. OpenSearch Yapılandırması (`config/opensearch.yml`)

*(Bu dosyanın hazır şablonu projenizde [`examples/opensearch-airgap.yml`](file:///Users/macbookairm1/Desktop/semantic-search/examples/opensearch-airgap.yml) olarak mevcuttur; doğrudan kopyalayabilirsiniz).*

`C:\opensearch-3.8.0\config\opensearch.yml` dosyasını Notepad++ veya VS Code ile açın ve içeriğini aşağıdaki gibi düzenleyin:

```yaml
# ==============================================================================
# OpenSearch 3.8.0 - Semantic Search Airgap Yapılandırması
# ==============================================================================

# 1. Küme ve Düğüm Tanımları
cluster.name: semantic-search-cluster
node.name: node-1

# 2. Tek Düğümlü Airgap Modu (Küme arama hatalarını engeller)
discovery.type: single-node

# 3. Ağ Ayarları (Tüm arayüzleri dinle; hem yerel hem uzak erişim sağlar)
network.host: 0.0.0.0
http.port: 9200
transport.port: 9300

# 4. Güvenlik Eklentisini Kapatma (Airgap HTTP Şifresiz / Kolay Mod)
# Bu satır SSL, HTTPS ve Admin şifresi zorunluluğunu devre dışı bırakır.
plugins.security.disabled: true

# 5. k-NN Vektör Arama Ayarları
knn.algo_param.index_thread_qty: 2
knn.memory.circuit_breaker.limit: "60%"

# 6. Yol Tanımları (İsteğe bağlı: Veriyi ve logları ayrı dizinde tutmak isterseniz)
path.data: C:\opensearch-3.8.0\data
path.logs: C:\opensearch-3.8.0\logs
```

---

## 5. JVM ve Bellek Yapılandırması (`config/jvm.options`)

OpenSearch varsayılan olarak 1 GB veya sistem belleğinin yarısını talep edebilir. Windows geliştirici makinenizin RAM durumuna göre sabit bir Heap boyutu belirleyin.

`C:\opensearch-3.8.0\config\jvm.options` dosyasını açın ve şu satırları düzenleyin:

```properties
# 8 GB RAM'li bir Windows makine için 2 GB Heap önerilir:
-Xms2g
-Xmx2g

# 16 GB veya üzeri RAM'e sahipseniz 4 GB yapabilirsiniz:
# -Xms4g
# -Xmx4g
```

> [!NOTE]
> `-Xms` (başlangıç belleği) ile `-Xmx` (maksimum bellek) değerlerinin **birebir eşit** olması Java Garbage Collector kararlılığı için zorunludur.

---

## 6. OpenSearch'ü Başlatma (`bin\opensearch.bat`)

### 6.1. Başlatma Komutu
Yeni bir **Komut İstemi (CMD)** veya **PowerShell** açın ve OpenSearch dizinine giderek başlatın:

```cmd
cd C:\opensearch-3.8.0\bin
opensearch.bat
```

### 6.2. Windows Güvenlik Duvarı Uyarısı
İlk çalıştırmada karşınıza **"Windows Defender Güvenlik Duvarı bu uygulamanın bazı özelliklerini engelledi"** penceresi gelecektir:
* **Özel Ağlar (Private Networks)** ve gerekiyorsa **Ortak Ağlar (Public Networks)** kutucuklarını işaretleyin.
* **Erişime İzin Ver (Allow access)** butonuna tıklayın.

### 6.3. Başarılı Başlatma Belirtisi
Konsolda şu satırları gördüğünüzde OpenSearch hazır demektir:
```text
[INFO ][o.o.n.Node               ] [node-1] initialized
[INFO ][o.o.n.Node               ] [node-1] starting ...
[INFO ][o.o.t.Netty4Transport    ] [node-1] publish_address {127.0.0.1:9300}, bound_addresses {0.0.0.0:9300}
[INFO ][o.o.h.AbstractHttpServerTransport] [node-1] publish_address {127.0.0.1:9200}, bound_addresses {0.0.0.0:9200}
[INFO ][o.o.n.Node               ] [node-1] started
```

*(Not: Bu terminal penceresini kapatmayın; pencere açık kaldığı sürece OpenSearch çalışacaktır).*

---

## 7. Kurulumu Doğrulama ve Fonksiyonel Testler

Yeni bir PowerShell penceresi açıp OpenSearch'ün çalıştığını ve k-NN eklentisinin aktif olduğunu test edin:

### Test 1: Küme Bilgisi ve Sürüm Kontrolü
```powershell
Invoke-RestMethod -Uri "http://localhost:9200" -Method Get
```
**Beklenen Yanıt:**
```json
{
  "name" : "node-1",
  "cluster_name" : "semantic-search-cluster",
  "cluster_uuid" : "...",
  "version" : {
    "number" : "3.8.0",
    "build_type" : "zip",
    "lucene_version" : "10.x"
  },
  "tagline" : "The OpenSearch Project: https://opensearch.org/"
}
```

### Test 2: Küme Sağlık Durumu (Cluster Health)
```powershell
Invoke-RestMethod -Uri "http://localhost:9200/_cluster/health" -Method Get
```
*`"status": "green"`* veya *"yellow"* görmelisiniz.

### Test 3: k-NN Vektör Eklentisi Kontrolü
```powershell
Invoke-RestMethod -Uri "http://localhost:9200/_cat/plugins?v" -Method Get
```
**Beklenen Çıktı:**
```text
name   component      version
node-1 opensearch-knn 3.8.0.0
```
> `opensearch-knn` satırı görünüyorsa vektör aramaları için her şey hazırdır!

---

## 8. Spring Boot Uygulaması ve `.env` Entegrasyonu

Windows makinenizde proje kök dizinindeki `.env` dosyasını açıp OpenSearch ayarlarını aşağıdaki gibi tanımlayın:

```properties
# ==============================================================================
# 2. OPENSEARCH (BM25 + Dense Vektör Deposu)
# ==============================================================================
# Aşama 1: Yerel Windows makinede çalıştırıyorsanız:
OPENSEARCH_HOST=localhost
OPENSEARCH_PORT=9200
OPENSEARCH_SCHEME=http
OPENSEARCH_USERNAME=
OPENSEARCH_PASSWORD=

# İsteğe bağlı zaman aşımı ayarları (ms cinsinden):
OPENSEARCH_CONNECT_TIMEOUT=5000
OPENSEARCH_SOCKET_TIMEOUT=60000
```

### Otomatik İndeks ve Şema Oluşturma
Projede manuel olarak indeks yaratmanıza **gerek yoktur**.  
Spring Boot ilk ayağa kalktığında veya ilk arama/indeksleme isteği geldiğinde [`OpenSearchAdapter.java`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/java/com/example/semantic_search/client/opensearch/OpenSearchAdapter.java) devreye girerek şu özellikleri taşıyan `olaylar` indeksini otomatik oluşturur:
1. `index.knn: true`
2. Özel Türkçe Arama Analizörü (`apostrophe`, `lowercase`, `turkish_keywords`, `turkish_stop`, `turkish_stemmer`)
3. HNSW Vektör İndeksi (`dimensions: 1024`, `spaceType: cosinesimil`, `engine: faiss`)
4. Coğrafi veri alanı (`konum: geo_point`)

---

## 9. 2. Aşamaya Geçiş: Uzak Sunucuya Taşıma Adımları

OpenSearch'ü ilerleyen aşamada airgap ağdaki başka bir sunucuya (örneğin `192.168.1.60`) taşıdığınızda yapılması gerekenler:

1. `C:\opensearch-3.8.0` klasörünü uzak sunucuya kopyalayın.
2. Uzak sunucudaki `config/opensearch.yml` dosyasında `network.host: 0.0.0.0` olduğundan emin olun.
3. Uzak sunucu Güvenlik Duvarında (Firewall) gelen **TCP 9200** portuna izin verin:
   ```powershell
   # Uzak Windows Server PowerShell:
   New-NetFirewallRule -DisplayName "OpenSearch 9200" -Direction Inbound -Protocol TCP -LocalPort 9200 -Action Allow
   ```
4. Kendi Windows istemci makinenizdeki `.env` dosyasında yalnızca şu satırı değiştirin:
   ```properties
   OPENSEARCH_HOST=192.168.1.60
   ```
5. İstemci makinenizden erişimi test edin:
   ```powershell
   Test-NetConnection -ComputerName 192.168.1.60 -Port 9200
   ```

---

## 10. Windows Servisi Olarak Arka Planda Çalıştırma (Opsiyonel)

Terminal penceresi kapatıldığında OpenSearch'ün kapanmasını istemiyorsanız, açık kaynaklı **NSSM** (Non-Sucking Service Manager) aracıyla arka plan servisine dönüştürebilirsiniz:

1. `nssm.exe` dosyasını `C:\opensearch-3.8.0\bin` dizinine kopyalayın.
2. Yönetici CMD'de şu komutu çalıştırın:
   ```cmd
   nssm install OpenSearch "C:\opensearch-3.8.0\bin\opensearch.bat"
   nssm set OpenSearch AppDirectory "C:\opensearch-3.8.0"
   nssm start OpenSearch
   ```
3. Artık OpenSearch, Windows başladığında otomatik olarak arka planda çalışacaktır.

---

## 11. Sık Karşılaşılan Sorunlar ve Çözümleri

### S1: `Error: Could not find or load main class ...`
* **Neden**: OpenSearch klasörü `C:\Program Files\` veya `C:\Yeni Klasör\` gibi boşluklu ya da Türkçe karakter içeren bir dizine açılmıştır.
* **Çözüm**: Klasörü doğrudan `C:\opensearch-3.8.0` gibi boşluksuz bir yola taşıyın.

### S2: `BindException: Address already in use: bind (port 9200)`
* **Neden**: 9200 portu başka bir servis (veya arkada kalmış eski bir OpenSearch instance'ı) tarafından kullanılıyor.
* **Çözüm**: Portu kullanan PID'yi bulup sonlandırın:
  ```powershell
  Get-Process -Id (Get-NetTCPConnection -LocalPort 9200).OwningProcess | Stop-Process -Force
  ```

### S3: `initial heap size set to a larger value than the computer's available memory`
* **Neden**: `jvm.options` dosyasında atanan bellek miktarı bilgisayardaki boş RAM'den fazladır.
* **Çözüm**: `config/jvm.options` dosyasında `-Xms1g` ve `-Xmx1g` olarak düşürün.

### S4: `UnsatisfiedLinkError: ... opensearch-knn / faiss ... Can't find dependent libraries`
* **Neden**: Windows işletim sisteminde Visual C++ Runtime kütüphaneleri eksiktir (FAISS C++ ile derlendiği için gereklidir).
* **Çözüm**: İnternetli makineden resmi **Visual C++ Redistributable 2015-2022 (x64)** paketini (`VC_redist.x64.exe`) indirip airgap Windows makineye yükleyin.

* **Çözüm**: Önce `opensearch.bat` çalıştırılmalı, `http://localhost:9200` adresinden yanıt alındıktan sonra `java -jar semantic-search-0.0.1-SNAPSHOT.jar` (veya `mvnw.cmd spring-boot:run`) komutu verilmelidir.
