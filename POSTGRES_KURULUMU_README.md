# 🐘 PostgreSQL 17 Kurulum, Yapılandırma ve Airgap Bağlantı Kılavuzu

Bu kılavuz; **kapalı devre (airgap / internetsiz) kurumsal ağda**, Windows bir istemci makineden uzaktaki bir sunucuda çalışan **PostgreSQL 17** veritabanına bağlanmak, gerekli kullanıcı/veritabanını açmak, şemaları yapılandırmak ve Spring Boot uygulamasını sorunsuz ayağa kaldırmak için gereken tüm adımları içermektedir.

---

## 📑 İçindekiler
1. [Genel Mimari ve Airgap Ağ Senaryosu](#1-genel-mimari-ve-airgap-ağ-senaryosu)
2. [Uzak PostgreSQL 17 Sunucu Tarafı Yapılandırması](#2-uzak-postgresql-17-sunucu-tarafı-yapılandırması)
3. [Veritabanı ve Kullanıcı Hazırlığı (SQL Scriptleri)](#3-veritabanı-ve-kullanıcı-hazırlığı-sql-scriptleri)
4. [Veritabanı Şeması ve Tablo Detayları](#4-veritabanı-şeması-ve-tablo-detayları)
5. [Windows İstemci Tarafı `.env` Yapılandırması](#5-windows-istemci-tarafı-env-yapılandırması)
6. [Bağlantı Testi ve Projeyi Başlatma](#6-bağlantı-testi-ve-projeyi-başlatma)
7. [Sık Karşılaşılan Sorunlar ve Çözümleri](#7-sık-karşılaşılan-sorunlar-ve-çözümleri)

---

## 1. Genel Mimari ve Airgap Ağ Senaryosu

```
┌─────────────────────────────────────────┐         İç Güvenli Ağ (LAN / Airgap)        ┌─────────────────────────────────────────┐
│     Windows İstemci Makine              │                                             │    Uzak Veritabanı Sunucusu             │
│   (Semantic Search Spring Boot)         │                                             │    (PostgreSQL 17 Instance)             │
│                                         │                                             │                                         │
│  • IP: 192.168.1.20 (Örnek)             │ ────────── TCP Port: 5432 ───────────────>  │  • IP: 192.168.1.50 (Örnek)             │
│  • .env (DB_HOST=192.168.1.50)          │       (HikariCP Keepalive 30s)              │  • DB: semantic_search                  │
│  • Flyway Otomatik Migration (V1-V5)    │                                             │  • User: semantic_search                │
└─────────────────────────────────────────┘                                             └─────────────────────────────────────────┘
```

- **Uygulama Rolü**: PostgreSQL; sistemin **tek gerçek kaynak (single source of truth)** ve **işlemsel durum takip (state engine)** katmanıdır.
- **İki Temel Tablo**:
  1. `indexing_state`: Doküman indeksleme durumları, Kafka versiyon/idempotency takibi ve SHA-256 metin hash'leri.
  2. `search_query_log`: Gerçekleşen tüm hibrit arama sorguları, süre metrikleri, ağırlıklar ve dönen sonuçların denetim (audit) logları.

---

## 2. Uzak PostgreSQL 17 Sunucu Tarafı Yapılandırması

PostgreSQL varsayılan olarak yalnızca yerel makineden (`localhost` / `127.0.0.1`) gelen bağlantıları kabul eder. Uzak makineden bağlanabilmek için sunucu tarafında 3 ayarın yapılması şarttır:

### A. Dinleme Adresini Açma (`postgresql.conf`)
PostgreSQL veri dizinindeki (`data/` veya `/etc/postgresql/17/main/`) `postgresql.conf` dosyasını açın:
```ini
# Tüm ağ arabirimlerinden gelen bağlantıları kabul et:
listen_addresses = '*'
port = 5432
```

### B. İstemci IP İznini Verme (`pg_hba.conf`)
Aynı dizindeki `pg_hba.conf` dosyasının en altına Windows makinenizin IP adresini (veya alt ağ bloğunu) ekleyin:

```ini
# TYPE  DATABASE         USER             ADDRESS          METHOD
# Belirli bir Windows makine için:
host    semantic_search  semantic_search  192.168.1.20/32  scram-sha-256

# VEYA tüm iç ağ (subnet) için:
host    semantic_search  semantic_search  192.168.1.0/24   scram-sha-256
```

> **Önemli**: Değişikliklerin devreye girmesi için PostgreSQL servisini yeniden başlatın veya reload edin:
> ```bash
> # Linux için:
> sudo systemctl restart postgresql
> # Windows için (Services menüsünden veya CMD):
> net stop postgresql-x64-17 && net start postgresql-x64-17
> ```

### C. Güvenlik Duvarı (Firewall) İzni
PostgreSQL 17 sunucusunun güvenlik duvarında gelen (inbound) **TCP 5432** portuna izin verilmelidir:
```powershell
# Windows Server Güvenlik Duvarı için (PowerShell):
New-NetFirewallRule -DisplayName "PostgreSQL 5432" -Direction Inbound -Protocol TCP -LocalPort 5432 -Action Allow
```
```bash
# Linux UFW için:
sudo ufw allow 5432/tcp
```

---

## 3. Veritabanı ve Kullanıcı Hazırlığı (SQL Scriptleri)

Uzak sunucuda `postgres` (superuser) yetkisiyle bir defaya mahsus çalıştırılması gereken komutlar:

Projede bu adım için hazır script bulunmaktadır: [`sql/init-postgres-17.sql`](file:///Users/macbookairm1/Desktop/semantic-search/sql/init-postgres-17.sql)

```sql
-- 1. Kullanıcı oluştur (eğer yoksa):
DO
$do$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles WHERE rolname = 'semantic_search'
   ) THEN
      CREATE USER semantic_search WITH PASSWORD 'SearchDev2026';
   END IF;
END
$do$;

-- 2. Veritabanını oluştur:
CREATE DATABASE semantic_search WITH OWNER semantic_search ENCODING 'UTF8';

-- 3. Yetkileri ata:
GRANT ALL PRIVILEGES ON DATABASE semantic_search TO semantic_search;

-- 4. 'semantic_search' veritabanına bağlanıp şema yetkilerini verin:
\c semantic_search

GRANT ALL ON SCHEMA public TO semantic_search;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO semantic_search;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO semantic_search;
```

---

## 4. Veritabanı Şeması ve Tablo Detayları

Spring Boot ilk başladığında **Flyway** (`V1` - `V5`) otomatik çalışarak tabloları ve sequence'ları kendisi oluşturur. Manuel oluşturmak isterseniz projedeki birleşik scripti kullanabilirsiniz: [`sql/schema-complete.sql`](file:///Users/macbookairm1/Desktop/semantic-search/sql/schema-complete.sql).

### A. `indexing_state` Tablosu
Dokümanın OpenSearch'e başarıyla yazılıp yazılmadığını, Kafka versiyon takibini ve metin bütünlüğünü doğrular.

| Kolon Adı | Veri Tipi | Kısıt / Özellik | Açıklama |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY` (Sequence: `indexing_state_seq`) | Otomatik artan kayıt kimliği |
| `document_id` | `VARCHAR(255)` | `NOT NULL` | Olayın benzersiz iş kimliği (`entityId`) |
| `index_name` | `VARCHAR(255)` | `NOT NULL` | Hedef OpenSearch indeksi (`olaylar`) |
| `status` | `VARCHAR(50)` | `NOT NULL` (Default: `PENDING`) | `INDEXED`, `DELETED`, `FAILED`, `PENDING` |
| `search_text_hash` | `VARCHAR(64)` | Nullable | SHA-256 metin özeti (Gereksiz embedding üretimini önler) |
| `last_indexed_at` | `TIMESTAMPTZ` | Nullable | OpenSearch'e son başarılı indeksleme zamanı |
| `error_message` | `TEXT` | Nullable | Hata oluşursa detaylı hata mesajı |
| `retry_count` | `INT` | `NOT NULL` (Default: 0) | Hata durumundaki tekrar deneme sayısı |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` (Default: `CURRENT_TIMESTAMP`) | İlk kayıt zamanı |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` (Default: `CURRENT_TIMESTAMP`) | Son güncelleme zamanı |
| `last_event_id` | `VARCHAR(128)` | Nullable | İşlenen son Kafka olayı UUID'si |
| `last_event_version` | `BIGINT` | Nullable | Olay sürüm no (Eski/sırasız gelen Kafka olaylarını engeller) |
| `document_source` | `TEXT` | Nullable (Check: `IS JSON`) | OpenSearch'e gönderilen ham doküman JSON anlık görüntüsü |

- **Benzersizlik Kısıtı**: `CONSTRAINT uq_document_index UNIQUE (document_id, index_name)`
- **İndeksler**: `idx_indexing_state_status`, `idx_indexing_state_document_id`

---

### B. `search_query_log` Tablosu
Sistem üzerinden yapılan tüm aramaları, harcanan süreleri, hibrit RRF parametrelerini ve dönen sonuçları kayıt altına alır.

| Kolon Adı | Veri Tipi | Açıklama |
| :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY` (Sequence: `search_query_log_seq`) |
| `query` | `VARCHAR(2000)` | Kullanıcının girdiği ham arama metni |
| `index_name` | `VARCHAR(255)` | Sorgulanan indeks (`olaylar`) |
| `search_type` | `VARCHAR(50)` | Arama türü (`HYBRID`, `BM25`, `SEMANTIC`, `HYBRID_EXPLAIN`) |
| `took_ms` | `BIGINT` | Toplam yanıt süresi (milisaniye) |
| `bm25_took_ms` | `BIGINT` | BM25 aşamasının süresi (ms) |
| `semantic_took_ms` | `BIGINT` | Yoğun vektör (Dense) aşamasının süresi (ms) |
| `bm25_weight` / `semantic_weight` | `NUMERIC(5,4)` | RRF formülündeki ağırlık katsayıları (örn: 0.5000) |
| `rank_constant` | `INT` | RRF sıralama sabiti ($k=60$) |
| `candidate_limit` / `result_limit` | `INT` | Çekilen aday havuzu ve kullanıcıya dönen adet |
| `bm25_result_count` / `semantic_result_count` | `INT` | Her motordan dönen aday adedi |
| `final_result_count` / `total_candidates` | `INT` | Nihai sonuç adedi ve tekilleştirilmiş toplam aday |
| `bm25_results_json` | `TEXT` | BM25 sonuç listesi ve skorları (JSON) |
| `semantic_results_json` | `TEXT` | Vektör arama sonuç listesi ve kosinüs skorları (JSON) |
| `final_results_json` | `TEXT` | RRF birleşik sonuçları ve Cross-Encoder sıralaması (JSON) |
| `settings_json` | `TEXT` | İstek filtreleri ve parametreleri (JSON) |
| `error_message` | `TEXT` | Hata durumunda hata detayı |
| `status` | `VARCHAR(20)` | `SUCCESS` veya `ERROR` |
| `created_at` | `TIMESTAMPTZ` | Sorgu zaman damgası |

- **İndeksler**: `idx_sql_query`, `idx_sql_created` (`DESC`), `idx_sql_status`, `idx_sql_index`

---

## 5. Windows İstemci Tarafı `.env` Yapılandırması

Windows makinenizde proje kök dizinindeki `.env` dosyasını bir metin düzenleyiciyle açıp uzak sunucu bilgilerinizi girin:

```properties
# ==============================================================================
# 1. POSTGRESQL VERİTABANI (Uzak PostgreSQL 17 Sunucusu)
# ==============================================================================
# Uzaktaki PostgreSQL 17 sunucunuzun LAN IP adresini veya hostname'ini girin:
DB_HOST=192.168.1.50
DB_PORT=5432
DB_NAME=semantic_search
DB_USERNAME=semantic_search
DB_PASSWORD=SearchDev2026
DB_SCHEMA=public

# Airgap ağda SSL sertifikası yoksa bu parametreyi kullanın:
DB_PARAMS=?sslmode=disable

# İsteğe Bağlı: Tek satırda tam JDBC URL vermek isterseniz (üstteki DB_* değerlerini ezer):
# DB_URL=jdbc:postgresql://192.168.1.50:5432/semantic_search?sslmode=disable

# ── Bağlantı Havuzu (HikariCP) & Flyway Ayarları ──
# Ağdaki olası gecikmeler ve TCP kopmalarına karşı keepalive ayarı:
DB_CONNECTION_TIMEOUT=30000
DB_MAX_POOL_SIZE=10
FLYWAY_ENABLED=true
```

---

## 6. Bağlantı Testi ve Projeyi Başlatma

### 6.1. Ağ ve Port Bağlantısını Doğrulama (Windows PowerShell)
Windows terminalinde uzak sunucunun 5432 portuna erişebildiğinizi test edin:

```powershell
# PowerShell üzerinden port kontrolü:
Test-NetConnection -ComputerName 192.168.1.50 -Port 5432
# 'TcpTestSucceeded : True' çıktısını görmelisiniz.
```

### 6.2. Projeyi Derleme ve Çalıştırma (Windows CMD / PowerShell)
```cmd
:: 1. Bağımlılıkları ve Java kodunu derleyin:
mvnw.cmd clean compile

:: 2. Spring Boot uygulamasını başlatın:
mvnw.cmd spring-boot:run
```

### 6.3. Başarılı Başlatma Logları:
```text
INFO  --- [main] o.f.c.i.database.base.BaseDatabaseType   : Database: PostgreSQL 17.x
INFO  --- [main] o.f.core.internal.command.DbMigrate      : Current version of schema "public": << Empty Schema >>
INFO  --- [main] o.f.core.internal.command.DbMigrate      : Migrating schema "public" to version "1 - create indexing state table"
INFO  --- [main] o.f.core.internal.command.DbMigrate      : Migrating schema "public" to version "2 - add kafka event state"
INFO  --- [main] o.f.core.internal.command.DbMigrate      : Migrating schema "public" to version "3 - store opensearch document source"
INFO  --- [main] o.f.core.internal.command.DbMigrate      : Migrating schema "public" to version "4 - create search query log table"
INFO  --- [main] o.f.core.internal.command.DbMigrate      : Migrating schema "public" to version "5 - add search query log sequence"
INFO  --- [main] o.f.core.internal.command.DbMigrate      : Successfully applied 5 migrations to schema "public"
INFO  --- [main] c.z.h.HikariDataSource                  : HikariPool-1 - Start completed.
INFO  --- [main] c.e.s.SemanticSearchApplication          : Started SemanticSearchApplication in 2.8 seconds
```

---

## 7. Sık Karşılaşılan Sorunlar ve Çözümleri

### S1: `Connection to 192.168.1.50:5432 refused`
- **Neden**: PostgreSQL servisi çalışmıyor, `listen_addresses` sadece `localhost` dinliyor veya güvenlik duvarı 5432 portunu engelliyor.
- **Çözüm**:
  1. Uzak sunucuda `postgresql.conf` dosyasında `listen_addresses = '*'` olduğundan emin olun.
  2. Sunucu güvenlik duvarında 5432 portuna izin verin.
  3. Windows PowerShell'den `Test-NetConnection -ComputerName <IP> -Port 5432` ile doğrula yapın.

### S2: `FATAL: no pg_hba.conf entry for host "192.168.1.20", user "semantic_search"`
- **Neden**: Uzak PostgreSQL sunucusu gelen istemci IP'sine güven ilişkisi tanımlamamış.
- **Çözüm**: Sunucudaki `pg_hba.conf` dosyasını açıp istemci IP'nizi ekleyin:
  ```text
  host    semantic_search    semantic_search    192.168.1.20/32    scram-sha-256
  ```
  ve PostgreSQL servisini yeniden yükleyin (`reload` / `restart`).

### S3: `The server does not support SSL` veya `SSL error`
- **Neden**: Airgap ağdaki PostgreSQL instance'ında SSL sertifikası kurulu değilken JDBC sürücüsü SSL istemeye çalışıyor.
- **Çözüm**: `.env` dosyasında `DB_PARAMS=?sslmode=disable` yapın veya `DB_URL` değişkenine `?sslmode=disable` ekleyin.

### S4: Uzun süre boşta (idle) kaldıktan sonra bağlantı kopması (`Connection reset by peer`)
- **Neden**: Airgap ağlardaki switch/güvenlik cihazları boşta kalan TCP oturumlarını kapatıyor olabilir.
- **Çözüm**: [`application.yml`](file:///Users/macbookairm1/Desktop/semantic-search/src/main/resources/application.yml) içerisine eklediğimiz `hikari.keepalive-time: 30000` (30 saniye) parametresi periyodik olarak canlılık sinyali göndererek oturumun kopmasını engeller.
