#!/usr/bin/env bash
# ============================================================
# seed-data.sh — 20 test dökümanını bulk olarak indexler
#
# Kullanım:
#   chmod +x examples/seed-data.sh
#   ./examples/seed-data.sh
#
# Backend varsayılan olarak localhost:8080'de çalışıyor olmalı.
# Farklı port için: BASE_URL=http://localhost:9090 ./examples/seed-data.sh
# ============================================================

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ENDPOINT="$BASE_URL/api/v1/index/bulk"

echo "📦  20 test dökümanı gönderiliyor → $ENDPOINT"
echo ""

HTTP_CODE=$(curl -s -o /tmp/seed_response.json -w "%{http_code}" -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '
[
  {
    "id": "doc-001",
    "type": "article",
    "title": "Makine Öğrenmesi ile Doğal Dil İşleme",
    "searchText": "Doğal dil işleme, bilgisayarların insan dilini anlama ve üretme yeteneğini geliştiren yapay zeka dalıdır. Transformer mimarileri bu alanda devrim yarattı.",
    "tags": ["nlp", "machine-learning", "transformer", "ai"],
    "structuredFields": {"year": 2024, "readTime": 8, "difficulty": "intermediate"},
    "metadata": {"author": "Ahmet Yılmaz", "journal": "AI Quarterly"}
  },
  {
    "id": "doc-002",
    "type": "article",
    "title": "BERT ve GPT: Dil Modellerinin Karşılaştırması",
    "searchText": "BERT çift yönlü, GPT tek yönlü dil modelleridir. Her ikisi de Transformer mimarisini temel alır ancak farklı pretraining hedefleri kullanır.",
    "tags": ["bert", "gpt", "language-model", "nlp"],
    "structuredFields": {"year": 2024, "readTime": 12, "difficulty": "advanced"},
    "metadata": {"author": "Zeynep Kaya"}
  },
  {
    "id": "doc-003",
    "type": "tutorial",
    "title": "OpenSearch ile Vektör Arama Kurulumu",
    "searchText": "OpenSearch k-NN plugin ile dense vector arama yapabilirsiniz. HNSW algoritması kullanılarak yüksek boyutlu vektörlerde yakın komşu araması gerçekleştirilir.",
    "tags": ["opensearch", "vector-search", "knn", "hnsw"],
    "structuredFields": {"year": 2024, "readTime": 15, "difficulty": "beginner"},
    "metadata": {"author": "Mehmet Demir"}
  },
  {
    "id": "doc-004",
    "type": "tutorial",
    "title": "BM25 Algoritması: Kelime Bazlı Arama Temelleri",
    "searchText": "BM25, TF-IDF'\''yi iyileştiren bir kelime bazlı sıralama algoritmasıdır. Belge uzunluğuna göre normalizasyon ve doygunluk faktörü içerir. Elasticsearch ve OpenSearch varsayılan olarak BM25 kullanır.",
    "tags": ["bm25", "information-retrieval", "ranking", "tf-idf"],
    "structuredFields": {"year": 2023, "readTime": 10, "difficulty": "intermediate"},
    "metadata": {"author": "Fatma Çelik"}
  },
  {
    "id": "doc-005",
    "type": "article",
    "title": "Hibrit Arama: BM25 ve Semantik Aramanın Birleşimi",
    "searchText": "Hibrit arama, anahtar kelime araması (BM25) ile anlam bazlı vektör aramasını birleştirir. Reciprocal Rank Fusion (RRF) algoritması her iki yöntemin sonuçlarını dengeli bir şekilde birleştirir.",
    "tags": ["hybrid-search", "bm25", "semantic-search", "rrf"],
    "structuredFields": {"year": 2024, "readTime": 18, "difficulty": "advanced"},
    "metadata": {"author": "Ali Şahin"}
  },
  {
    "id": "doc-006",
    "type": "product",
    "title": "BGE-M3: Çok Dilli Embedding Modeli",
    "searchText": "BGE-M3, BAAI tarafından geliştirilen çok dilli ve çok işlevli bir embedding modelidir. 100'\''den fazla dili destekler ve 1024 boyutlu vektörler üretir. Dense, sparse ve colbert retrieval için optimize edilmiştir.",
    "tags": ["bge-m3", "embedding", "multilingual", "baai"],
    "structuredFields": {"year": 2024, "modelSize": "large", "dimensions": 1024},
    "metadata": {"provider": "BAAI", "license": "MIT"}
  },
  {
    "id": "doc-007",
    "type": "product",
    "title": "Sentence Transformers ile Metin Benzerliği",
    "searchText": "Sentence-BERT, cümle düzeyinde anlamsal benzerlik hesaplamak için optimize edilmiş transformer modelidir. Siamese network mimarisini kullanarak iki metni karşılaştırır.",
    "tags": ["sentence-transformers", "sbert", "semantic-similarity"],
    "structuredFields": {"year": 2023, "modelSize": "medium", "dimensions": 384},
    "metadata": {"provider": "UKP Lab"}
  },
  {
    "id": "doc-008",
    "type": "case-study",
    "title": "E-Ticaret Ürün Aramasında Semantik Arama",
    "searchText": "Bir e-ticaret platformunda semantik arama entegrasyonu sonucu tıklama oranı %34 arttı. Kullanıcılar eş anlamlı kelimelerle arama yaparken de ilgili ürünlere ulaşabildi.",
    "tags": ["e-commerce", "semantic-search", "product-search", "case-study"],
    "structuredFields": {"year": 2024, "industry": "e-commerce", "roi": "34%"},
    "metadata": {"company": "TechShop TR"}
  },
  {
    "id": "doc-009",
    "type": "case-study",
    "title": "Hukuk Dokümanlarında Bilgi Çıkarma",
    "searchText": "Hukuki metinlerde NLP tabanlı bilgi çıkarma sistemi, sözleşme inceleme süresini %60 azalttı. Named entity recognition ve relation extraction teknikleri kullanıldı.",
    "tags": ["legal-tech", "information-extraction", "ner", "nlp"],
    "structuredFields": {"year": 2024, "industry": "legal", "timeSaving": "60%"},
    "metadata": {"company": "LexTech"}
  },
  {
    "id": "doc-010",
    "type": "article",
    "title": "Reciprocal Rank Fusion (RRF) Matematiksel Temelleri",
    "searchText": "RRF, birden fazla sıralama listesini birleştiren bir fusion algoritmasıdır. Formül: RRF(d) = Σ 1/(k + rank(d)) şeklindedir. k parametresi genellikle 60 olarak seçilir ve düşük sıraların etkisini azaltır.",
    "tags": ["rrf", "rank-fusion", "information-retrieval", "mathematics"],
    "structuredFields": {"year": 2023, "readTime": 14, "difficulty": "advanced"},
    "metadata": {"author": "Dr. Selma Koç"}
  },
  {
    "id": "doc-011",
    "type": "tutorial",
    "title": "Spring Boot ile REST API Geliştirme",
    "searchText": "Spring Boot, Java'\''da hızlı REST API geliştirmek için kullanılan bir framework'\''tür. Auto-configuration özelliği ile kurulum sürecini sadeleştirir. @RestController, @GetMapping ve @PostMapping annotasyonları kullanılır.",
    "tags": ["spring-boot", "java", "rest-api", "backend"],
    "structuredFields": {"year": 2024, "readTime": 20, "difficulty": "beginner"},
    "metadata": {"author": "Burak Arslan"}
  },
  {
    "id": "doc-012",
    "type": "article",
    "title": "Docker ile Mikro Servis Mimarisi",
    "searchText": "Docker container'\''lar, uygulamaları izole edilmiş ortamlarda çalıştırmanıza olanak tanır. Docker Compose ile birden fazla servisi orkestre edebilirsiniz. Mikro servis mimarisi ölçeklenebilirlik ve bağımsız dağıtım sağlar.",
    "tags": ["docker", "microservices", "devops", "containers"],
    "structuredFields": {"year": 2024, "readTime": 16, "difficulty": "intermediate"},
    "metadata": {"author": "Emre Yıldız"}
  },
  {
    "id": "doc-013",
    "type": "article",
    "title": "Apache Kafka ile Gerçek Zamanlı Veri Akışı",
    "searchText": "Apache Kafka, yüksek hacimli mesaj akışı için tasarlanmış dağıtık bir event streaming platformdur. Producer'\''lar mesaj gönderir, Consumer'\''lar okur. Topic'\''ler partition'\''lara bölünerek paralel işleme sağlanır.",
    "tags": ["kafka", "streaming", "event-driven", "distributed-systems"],
    "structuredFields": {"year": 2024, "readTime": 22, "difficulty": "intermediate"},
    "metadata": {"author": "Selin Aktaş"}
  },
  {
    "id": "doc-014",
    "type": "product",
    "title": "Hugging Face TEI: Yüksek Performanslı Embedding Servisi",
    "searchText": "Text Embeddings Inference (TEI), Hugging Face'\''in embedding modelleri için geliştirdiği yüksek performanslı sunucu altyapısıdır. gRPC ve HTTP API desteği sunar, GPU optimizasyonu ile milisaniyeler içinde embedding üretir.",
    "tags": ["tei", "huggingface", "embedding-server", "gpu"],
    "structuredFields": {"year": 2024, "category": "infrastructure", "license": "Apache-2.0"},
    "metadata": {"provider": "Hugging Face"}
  },
  {
    "id": "doc-015",
    "type": "article",
    "title": "Cosine Similarity ile Metin Benzerliği Ölçümü",
    "searchText": "Cosine similarity, iki vektör arasındaki açıyı ölçerek metin benzerliğini hesaplar. Değer 1'\''e yakınsa metinler benzer, 0'\''a yakınsa benzer değil demektir. Normalizasyon sonrası dot product ile hızlıca hesaplanabilir.",
    "tags": ["cosine-similarity", "vector-search", "mathematics", "nlp"],
    "structuredFields": {"year": 2023, "readTime": 9, "difficulty": "intermediate"},
    "metadata": {"author": "Prof. Hakan Güler"}
  },
  {
    "id": "doc-016",
    "type": "tutorial",
    "title": "PostgreSQL ile Tam Metin Arama",
    "searchText": "PostgreSQL, tsvector ve tsquery tipleri ile yerleşik tam metin arama sunar. GIN indeksler aramaları hızlandırır. pg_search eklentisi ile BM25 tabanlı sıralama da yapılabilir.",
    "tags": ["postgresql", "full-text-search", "database", "sql"],
    "structuredFields": {"year": 2024, "readTime": 13, "difficulty": "beginner"},
    "metadata": {"author": "Ayşe Demir"}
  },
  {
    "id": "doc-017",
    "type": "case-study",
    "title": "Sağlık Sektöründe Semantik Benzer Hasta Kaydı Arama",
    "searchText": "Bir hastanede ICD kodları yerine semptom açıklamalarına göre benzer hasta kayıtlarını bulan sistem geliştirildi. Semantik arama doğruluğu geleneksel tam metin aramaya kıyasla %45 artış gösterdi.",
    "tags": ["healthcare", "semantic-search", "icd-codes", "medical-nlp"],
    "structuredFields": {"year": 2024, "industry": "healthcare", "accuracy": "45%"},
    "metadata": {"institution": "Sağlık Araştırma Merkezi"}
  },
  {
    "id": "doc-018",
    "type": "article",
    "title": "Türkçe NLP: Dil Modeli Geliştirme Zorlukları",
    "searchText": "Türkçe, sondan eklemeli morfolojik yapısı ile NLP açısından zorlu bir dildir. Kök bulmak ve morfolojik analiz kritik önem taşır. BERTurk ve mBERT bu alanda öne çıkan modellerdir.",
    "tags": ["turkish-nlp", "morphology", "berturk", "multilingual"],
    "structuredFields": {"year": 2024, "readTime": 17, "difficulty": "advanced"},
    "metadata": {"author": "Dr. Neslihan Arslan"}
  },
  {
    "id": "doc-019",
    "type": "tutorial",
    "title": "HNSW Algoritması ile Yakın Komşu Araması",
    "searchText": "Hierarchical Navigable Small World (HNSW), yaklaşık en yakın komşu araması için graf tabanlı bir algoritmadır. Logaritmik karmaşıklıkla milyonlarca vektör arasında milisaniyeler içinde arama yapabilir. ef_construction ve M parametreleri performansı etkiler.",
    "tags": ["hnsw", "approximate-nearest-neighbor", "vector-index", "algorithm"],
    "structuredFields": {"year": 2023, "readTime": 25, "difficulty": "advanced"},
    "metadata": {"author": "Caner Özdemir"}
  },
  {
    "id": "doc-020",
    "type": "article",
    "title": "Arama Motorlarında Öğrenilmiş Sıralama (Learning to Rank)",
    "searchText": "Learning to Rank, makine öğrenmesi ile arama sıralama puanlarını optimize eden bir yaklaşımdır. Pointwise, pairwise ve listwise stratejiler kullanılır. NDCG ve MAP gibi metriklerle değerlendirilir.",
    "tags": ["learning-to-rank", "information-retrieval", "ml", "ranking"],
    "structuredFields": {"year": 2024, "readTime": 20, "difficulty": "advanced"},
    "metadata": {"author": "Tolga Başaran"}
  }
]
')

if [ "$HTTP_CODE" = "201" ]; then
  echo ""
  echo "🎉  20 döküman başarıyla indexlendi! (HTTP $HTTP_CODE)"
else
  echo ""
  echo "❌  Hata! HTTP $HTTP_CODE"
  echo "Sunucu yanıtı:"
  cat /tmp/seed_response.json
  exit 1
fi

echo "    UI  → http://localhost:5173"
echo "    API → $BASE_URL/api/v1/search/explain"
echo ""
echo "Deneme sorguları:"
echo "  • 'semantik arama'"
echo "  • 'embedding model'"
echo "  • 'makine öğrenmesi'"
echo "  • 'BM25 algoritması'"
echo "  • 'Türkçe dil işleme'"
