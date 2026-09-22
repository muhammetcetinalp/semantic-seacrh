package com.example.semantic_search.dto;

import java.util.List;
import java.util.Map;

/**
 * Hibrit arama sürecinin her aşamasını (BM25, Dense/ColBERT, RRF Füzyon ve Reranker)
 * şeffaf bir şekilde arayüze sunan açıklayıcı yanıt kaydı (Record DTO).
 *
 * @param query Çalıştırılan arama sorgusu
 * @param indexName Sorgulanan OpenSearch indeksi
 * @param tookMs Toplam hibrit arama süresi (ms)
 * @param settings Sorgu anında kullanılan katsayılar ve parametreler
 * @param bm25 BM25 sözcüksel arama aşamasının sonuçları ve süresi
 * @param semantic Vektörel/ColBERT semantik arama aşamasının sonuçları ve süresi
 * @param finalResults RRF veya skor normalizasyonu ile birleştirilmiş nihai liste
 * @param totalCandidates Havuzdan toplanan tekil aday döküman sayısı
 * @param rerank Cross-Encoder derin alaka skorlama aşamasının sonuçları (aktif değilse null)
 */
public record HybridExplainResponse(
        String query,
        String indexName,
        long tookMs,
        HybridSettings settings,
        SearchStage bm25,
        SearchStage semantic,
        List<FusionResult> finalResults,
        int totalCandidates,
        RerankStage rerank) {

    /**
     * Reranker aşamasının bulunmadığı durumlar için pratik yapıcı metot.
     *
     * @param query Arama sorgusu
     * @param indexName İndeks adı
     * @param tookMs Geçen süre (ms)
     * @param settings Hibrit parametreleri
     * @param bm25 BM25 aşama çıktısı
     * @param semantic Semantik aşama çıktısı
     * @param finalResults Birleştirilmiş nihai liste
     * @param totalCandidates Toplam aday sayısı
     */
    public HybridExplainResponse(String query, String indexName, long tookMs,
                                  HybridSettings settings, SearchStage bm25, SearchStage semantic,
                                  List<FusionResult> finalResults, int totalCandidates) {
        this(query, indexName, tookMs, settings, bm25, semantic, finalResults, totalCandidates, null);
    }

    /**
     * Hibrit arama çalıştırma ayarlarını temsil eden kayıt DTO'su.
     *
     * @param limit Döndürülecek nihai sonuç adedi
     * @param candidateLimit Her koldan çekilen aday tavanı
     * @param candidateMultiplier Aday çarpanı (limit x multiplier)
     * @param rankConstant RRF formülündeki k sabiti
     * @param bm25Weight BM25 katsayısı
     * @param semanticWeight Semantik katsayı
     * @param types Filtrelenen doküman türleri
     * @param filters Uygulanan ek yapısal filtreler
     * @param fusionMode Sıralama birleştirme modu (RRF veya SCORE_NORMALIZED)
     */
    public record HybridSettings(
            int limit,
            int candidateLimit,
            int candidateMultiplier,
            int rankConstant,
            double bm25Weight,
            double semanticWeight,
            List<String> types,
            Map<String, Object> filters,
            String fusionMode) {

        /**
         * RRF modunu varsayılan kabul eden pratik yapıcı metot.
         */
        public HybridSettings(int limit, int candidateLimit, int candidateMultiplier, int rankConstant,
                              double bm25Weight, double semanticWeight, List<String> types, Map<String, Object> filters) {
            this(limit, candidateLimit, candidateMultiplier, rankConstant, bm25Weight, semanticWeight, types, filters, "RRF");
        }
    }

    /**
     * Tek bir arama kolunun (BM25 veya Semantik) sonuçlarını barındıran aşama kaydı.
     *
     * @param method Kullanılan yöntem adı (örn: BM25, DENSE, COLBERT)
     * @param tookMs Bu kolun icra süresi (ms)
     * @param results Sıralanmış aday sonuçlar listesi
     */
    public record SearchStage(String method, long tookMs, List<RankedResult> results) { }

    /**
     * ColBERT token seviyesi MaxSim etkileşim eşleşme kaydı.
     *
     * @param queryToken Sorgudaki token
     * @param matchedDocToken Dökümanda en yüksek benzerliği veren token
     * @param similarity İki token arasındaki kosinüs benzerliği skoru
     */
    public record TokenMatch(String queryToken, String matchedDocToken, double similarity) { }

    /**
     * Tek bir arama kolunda derece almış aday doküman kaydı.
     *
     * @param rank Bu koldaki sırası (1'den başlar)
     * @param originalScore Modelden veya OpenSearch'ten gelen ham skor
     * @param document Dokümanın kendisi
     * @param tokenMatches ColBERT token etkileşimleri listesi (ColBERT değilse boş)
     */
    public record RankedResult(int rank, double originalScore, SearchResult document, List<TokenMatch> tokenMatches) {
        public RankedResult(int rank, double originalScore, SearchResult document) {
            this(rank, originalScore, document, List.of());
        }
    }

    /**
     * RRF veya skor normalizasyonu ile hesaplanan nihai birleşim sonucu kaydı.
     *
     * @param finalRank Nihai sıralamadaki yeri
     * @param rrfScore Hesaplanmış RRF veya birleşik skoru
     * @param bm25Rank BM25 kolundaki sırası (eşleşmediyse null)
     * @param semanticRank Semantik koldaki sırası (eşleşmediyse null)
     * @param bm25Contribution BM25 kolunun nihai skora katkısı
     * @param semanticContribution Semantik kolun nihai skora katkısı
     * @param document Dokümanın kendisi
     * @param normalizedBm25Score Normalizasyon modunda [0, 1] arası BM25 puanı
     * @param normalizedSemanticScore Normalizasyon modunda [0, 1] arası Semantik puanı
     */
    public record FusionResult(
            int finalRank,
            double rrfScore,
            Integer bm25Rank,
            Integer semanticRank,
            double bm25Contribution,
            double semanticContribution,
            SearchResult document,
            Double normalizedBm25Score,
            Double normalizedSemanticScore) {

        public FusionResult(int finalRank, double rrfScore, Integer bm25Rank, Integer semanticRank,
                            double bm25Contribution, double semanticContribution, SearchResult document) {
            this(finalRank, rrfScore, bm25Rank, semanticRank, bm25Contribution, semanticContribution, document, null, null);
        }
    }

    /**
     * Cross-Encoder Reranker derin alaka skorlama aşaması çıktısı.
     *
     * @param model Kullanılan Cross-Encoder model adı (örn: BAAI/bge-reranker-v2-m3)
     * @param tookMs Reranker işlem süresi (ms)
     * @param usedFallback Reranker ulaşılamadığında RRF sıralamasına geri düşülüp düşülmediği
     * @param results Reranker tarafından yeniden sıralanmış sonuçlar listesi
     */
    public record RerankStage(
            String model,
            long tookMs,
            boolean usedFallback,
            List<RerankedFusionResult> results) { }

    /**
     * Reranker tarafından derin analizle puanlanmış doküman sonucu kaydı.
     *
     * @param finalRank Reranker sonrası nihai sırası
     * @param relevanceScore Cross-Encoder alaka puanı (0.0 ile 1.0 arası)
     * @param rrfScore Önceki RRF aşamasından gelen skor
     * @param bm25Rank Orijinal BM25 sırası
     * @param semanticRank Orijinal Semantik sırası
     * @param document Dokümanın kendisi
     */
    public record RerankedFusionResult(
            int finalRank,
            double relevanceScore,
            double rrfScore,
            Integer bm25Rank,
            Integer semanticRank,
            SearchResult document) { }
}
