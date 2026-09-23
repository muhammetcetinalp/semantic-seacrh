# 🧠 Airgap Embedding & Reranker Modelleri Yapılandırma Kılavuzu (API Key ve .cer Sertifikası)

Bu kılavuz; **kapalı devre (airgap / internetsiz) kurumsal ağda**, ayrı bir sunucuda çalışan **Embedding (BGE-M3)** ve **Cross-Encoder Reranker (BGE-Reranker-v2-m3)** yapay zeka servislerine **API anahtarı (API Key)** kullanarak doğrudan bağlanmak ve `.cer` uzantılı SSL sertifikasını Java'ya tanıtmak için gereken tüm yapılandırmayı açıklamaktadır.

---

## 📑 İçindekiler
1. [Genel Mimari ve Yapay Zeka Modelleri](#1-genel-mimari-ve-yapay-zeka-modelleri)
2. [API Key (Kimlik Doğrulama) Yapılandırması](#2-api-key-kimlik-doğrulama-yapılandırması)
3. [.cer Sertifikasını Java'ya Tanıtma (Standart Yöntem)](#3-cer-sertifikasını-javaya-tanıtma-standart-yöntem)
4. [Model Uç Noktaları ve Sağlayıcı (Provider) Seçimi](#4-model-uç-noktaları-ve-sağlayıcı-provider-seçimi)
5. [Windows İstemci Tarafı `.env` Yapılandırması](#5-windows-istemci-tarafı-env-yapılandırması)
6. [Bağlantıyı Doğrulama Testleri (Windows PowerShell)](#6-bağlantıyı-doğrulama-testleri-windows-powershell)
7. [Sık Karşılaşılan Sorunlar ve Çözümleri](#7-sık-karşılaşılan-sorunlar-ve-çözümleri)

---

## 1. Genel Mimari ve Yapay Zeka Modelleri

Projemiz hibrit arama sürecinde iki farklı yapay zeka servisine doğrudan HTTP/HTTPS istekleri atar:

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ AIRGAP MODEL SUNUCUSU (HTTPS + API Key)                                          │
│                                                                                  │
│  ┌─────────────────────────────────┐      ┌─────────────────────────────────┐    │
│  │ 1. Dense Embedding Modeli       │      │ 2. Cross-Encoder Reranker       │    │
│  │    Model: BAAI/bge-m3           │      │    Model: BAAI/bge-reranker-v2  │    │
│  │    Çıktı: 1024 boyutlu vektör   │      │    Çıktı: Derin alaka puanı     │    │
│  │    Endpoint: /v1/embeddings     │      │    Endpoint: /rerank            │    │
│  └─────────────────────────────────┘      └─────────────────────────────────┘    │
└──────────────────────────────────────▲───────────────────────────────────────────┘
                                       │ HTTPS
                                       │ Headers: Authorization: Bearer & X-API-Key
┌──────────────────────────────────────┴───────────────────────────────────────────┐
│ WINDOWS İSTEMCİ (Semantic Search Spring Boot Uygulaması)                         │
│                                                                                  │
│  • RestClient: Spring'in yerel HTTP istemcisi (API Key destekli)                 │
│  • RestEmbeddingProvider / TeiEmbeddingProvider: Metinleri vektörleştirir        │
│  • TeiRerankingService: BM25 + Vektör sonuçlarını yeniden sıralar                │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. API Key (Kimlik Doğrulama) Yapılandırması

Model sunucunuza erişmek için `.env` dosyanıza API anahtarınızı girmeniz yeterlidir:

### Ortak Anahtar Kullanımı (Embedding ve Reranker aynı API Key'i kullanıyorsa):
```properties
AI_API_KEY=sk-airgap-production-secret-key-2026
```

### Ayrı Anahtarlar Kullanımı (Modeller farklı API Key gerektiriyorsa):
```properties
EMBEDDING_API_KEY=sk-embedding-key-12345
SEARCH_RERANKING_API_KEY=sk-reranker-key-67890
```

> **Nasıl Gönderilir?**  
> Uygulama isteği atarken hem `Authorization: Bearer <KEY>` hem de `X-API-Key: <KEY>` başlıklarını otomatik olarak ekler; böylece tüm kurumsal API Gateway ve model servisleriyle uyumludur.

---

## 3. `.cer` Sertifikasını Java'ya Tanıtma (Standart Yöntem)

Eğer model sunucunuz HTTPS üzerinde kurum içi özel bir `.cer` sertifikası kullanıyorsa, Java'nın bu sertifikaya güvenmesi için Windows makinenizde **tek bir komutla** sertifikayı Java'nın güvenilen sertifika deposuna (`cacerts`) eklemeniz yeterlidir:

### Windows PowerShell / CMD (Yönetici Olarak Açın):
```powershell
keytool -importcert -alias airgap-model-server -file "C:\certs\model-server.cer" -keystore "$env:JAVA_HOME\lib\security\cacerts" -storepass changeit -noprompt
```

*(Not: Eğer model sunucusu kurumun Active Directory Root CA'sı tarafından imzalanmışsa veya Windows Domain sertifikasıysa, bu komuta bile gerek kalmadan Java doğrudan bağlanır).*

---

## 4. Model Uç Noktaları ve Sağlayıcı (Provider) Seçimi

### 4.1. Embedding Modeli (`search.embedding`)
Airgap sunucunuzdaki model API formatına göre iki sağlayıcıdan birini seçebilirsiniz:

* **Seçenek A: `EMBEDDING_PROVIDER=rest` (En Yaygın)**
  * Standart OpenAI, vLLM, FastEmbed veya FastAPI uyumlu JSON sözleşmesini kullanır:
  * İstek formatı: `POST {endpoint}` ➡️ `{"input": "metin", "model": "BAAI/bge-m3"}`
  * Yanıt formatı: `{"data": [{"embedding": [...]}]}`

* **Seçenek B: `EMBEDDING_PROVIDER=tei`**
  * Hugging Face Text Embeddings Inference (TEI) veya Infinity motorları için:
  * İstek formatı: `POST {endpoint}/embed` ➡️ `{"inputs": "metin"}`
  * Yanıt formatı: `[[float, float, ...]]`

> [!IMPORTANT]
> **Vektör Boyutu Kuralı**: BGE-M3 modeli **1024 boyutlu** vektör üretir.  
> Bu sebeple `.env` içinde mutlaka **`EMBEDDING_DIMENSIONS=1024`** olmalıdır.

---

### 4.2. Reranker Modeli (`search.reranking`)
Cross-Encoder modeli için:

* **`SEARCH_RERANKING_PROVIDER=tei`**
  * TEI, Infinity, FastAPI veya kurumsal model sunucunuzun `/rerank` uç noktasını çağırır:
  * İstek formatı: `POST {endpoint}/rerank` ➡️ `{"query": "...", "texts": [...]}`
  * Yanıt formatı: `[{"index": 0, "score": 0.98}, ...]`

---

## 5. Windows İstemci Tarafı `.env` Yapılandırması

Uygulamanızın kök dizinindeki `.env` dosyasını açıp bilgileri aşağıdaki gibi doldurun:

```properties
# ==============================================================================
# 3. HARİCİ MODEL SUNUCUSU (API Key)
# ==============================================================================
# Hem Embedding hem Reranker için ortak API anahtarı:
AI_API_KEY=sk-prod-ai-key-998877

# ==============================================================================
# 4. EMBEDDING SAĞLAYICI (Dense Vektörler / BGE-M3)
# ==============================================================================
EMBEDDING_PROVIDER=rest
EMBEDDING_MOCK_ENABLED=false
EMBEDDING_ENDPOINT=https://model-server.airgap.lan/v1/embeddings
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_DIMENSIONS=1024
EMBEDDING_TIMEOUT=30000

# ==============================================================================
# 5. RERANKER (Cross-Encoder Yeniden Sıralama)
# ==============================================================================
SEARCH_RERANKING_ENABLED=true
SEARCH_RERANKING_PROVIDER=tei
SEARCH_RERANKING_ENDPOINT=https://model-server.airgap.lan/rerank
SEARCH_RERANKING_MODEL=BAAI/bge-reranker-v2-m3
```

---

## 6. Bağlantıyı Doğrulama Testleri (Windows PowerShell)

Uygulamayı başlatmadan önce Windows terminalinden model API'nize erişebildiğinizi test edin:

### Test 1: Embedding API Testi (PowerShell)
```powershell
$headers = @{
    "Authorization" = "Bearer sk-prod-ai-key-998877"
    "Content-Type"  = "application/json"
}

$body = @{
    "input" = "Deniz trafiği radar tespiti"
    "model" = "BAAI/bge-m3"
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://model-server.airgap.lan/v1/embeddings" -Method Post -Headers $headers -Body $body
```
*Dönen yanıtta 1024 adet float sayı içeren `embedding` dizisini görmelisiniz.*

### Test 2: Reranker API Testi (PowerShell)
```powershell
$headers = @{
    "Authorization" = "Bearer sk-prod-ai-key-998877"
    "Content-Type"  = "application/json"
}

$body = @{
    "query" = "Radar arızası"
    "texts" = @(
        "Samsun istasyonunda radar bakımı tamamlandı",
        "Antalya sahilinde şüpheli tekne görüldü"
    )
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://model-server.airgap.lan/rerank" -Method Post -Headers $headers -Body $body
```
*Puan sıralamasını (`score` veya `relevance_score`) görmelisiniz.*

---

## 7. Sık Karşılaşılan Sorunlar ve Çözümleri

### S1: `PKIX path building failed: unable to find valid certification path`
* **Neden**: Sunucunun HTTPS `.cer` sertifikası Java'nın truststore deposunda kayıtlı değil.
* **Çözüm**: 3. Bölümdeki `keytool -importcert ...` komutu ile sertifikayı Java'nın `cacerts` deposuna ekleyin.

### S2: `HTTP 401 Unauthorized` veya `HTTP 403 Forbidden`
* **Neden**: API Key geçersiz veya sunucu farklı bir başlık bekliyor.
* **Çözüm**:
  * `.env` dosyasındaki `AI_API_KEY` değerini kontrol edin.
  * Eğer sunucunuz özel bir başlık bekliyorsa (örn: `api-key`): `.env` içine `EMBEDDING_API_KEY_HEADER=api-key` yazın.

### S3: OpenSearch `Vector dimension mismatch (expected [1024], but got [384])`
* **Neden**: Mock model (384) ile BGE-M3 gerçek model (1024) boyutları karışmıştır.
* **Çözüm**: `.env` dosyasında `EMBEDDING_DIMENSIONS=1024` olduğundan emin olun.
