package com.example.semantic_search.service;

import com.example.semantic_search.client.embedding.EmbeddingProvider;
import com.example.semantic_search.client.opensearch.OpenSearchAdapter;
import com.example.semantic_search.config.SearchProperties;
import com.example.semantic_search.dto.HybridExplainRequest;
import com.example.semantic_search.dto.HybridExplainResponse;
import com.example.semantic_search.dto.SearchRequest;
import com.example.semantic_search.dto.SearchResponse;
import com.example.semantic_search.dto.SearchResult;
import com.example.semantic_search.model.SearchType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Tüm arama akışlarını, algoritmalarını ve çok aşamalı arama mimarisini yöneten ana servis.
 *
 * <p>Aşağıdaki operasyonları koordine eder:
 * <ul>
 *   <li><b>BM25</b>: OpenSearch Türkçe dilbilgisel ve alana göre ağırlıklandırılmış tam metin araması.</li>
 *   <li><b>Yoğun Vektör (Dense Vector)</b>: BGE-M3 / TEI 1024 boyutlu gömme vektörleri ile k-NN kosinüs benzerliği.</li>
 *   <li><b>Hibrit Arama (Hybrid Search)</b>: Reciprocal Rank Fusion (RRF) veya Normalize Skor Füzyonu ile BM25 ve Vektör sıralamalarının harmanlanması.</li>
 *   <li><b>Cross-Encoder Yeniden Sıralama (Reranking)</b>: İlk aşama aday sonuçlarının TEI / Harici Model API derin dikkat modeliyle tekrar sıralanması.</li>
 *   <li><b>Arama Günlüğü (Audit Logging)</b>: Tüm isteklerin ve metriklerin PostgreSQL'e asenkron kaydedilmesi.</li>
 * </ul>
 * </p>
 */
@Service
public class SearchQueryService {

    private static final Logger log = LoggerFactory.getLogger(SearchQueryService.class);

    private final OpenSearchAdapter openSearchAdapter;
    private final EmbeddingProvider embeddingProvider;
    private final SearchProperties searchProperties;
    private final SearchQueryLogService queryLogService;
    private final Optional<RerankingService> rerankingService;

    /**
     * SearchQueryService için gerekli tüm altyapı bağımlılıklarını enjekte eden ana yapıcı metot.
     *
     * @param openSearchAdapter OpenSearch istemci adaptörü
     * @param embeddingProvider Vektör gömme sağlayıcısı
     * @param searchProperties Varsayılan arama yapılandırma parametreleri
     * @param queryLogService Arama loglama servisi
     * @param rerankingService İsteğe bağlı Cross-Encoder reranking servisi
     */
    @Autowired
    public SearchQueryService(OpenSearchAdapter openSearchAdapter,
                              EmbeddingProvider embeddingProvider,
                              SearchProperties searchProperties,
                              SearchQueryLogService queryLogService,
                              Optional<RerankingService> rerankingService) {
        this.openSearchAdapter = openSearchAdapter;
        this.embeddingProvider = embeddingProvider;
        this.searchProperties = searchProperties;
        this.queryLogService = queryLogService;
        this.rerankingService = rerankingService;
        rerankingService.ifPresent(r -> log.info("Yeniden sıralama (Reranking) aktif — {}", r.getClass().getSimpleName()));
    }

    /**
     * Standart /api/v1/search uç noktasını işleyen temel arama metodu.
     *
     * @param request Arama isteği (sorgu, arama tipi, filtreler, limit, offset)
     * @return Eşleşen sonuçları ve arama metriklerini içeren SearchResponse
     */
    public SearchResponse search(SearchRequest request) {
        long startTime = System.currentTimeMillis();

        String indexName = resolveIndexName(request.getIndexName());
        openSearchAdapter.createIndexIfNotExists(indexName);
        int limit = resolveLimit(request.getLimit());
        int offset = request.getOffset() != null ? Math.max(0, request.getOffset()) : 0;
        SearchType searchType = resolveSearchType(request.getSearchType());
        Map<String, Object> filters = buildFilters(request);

        try {
            List<SearchResult> results = switch (searchType) {
                case BM25 -> offset > 0
                        ? openSearchAdapter.bm25Search(indexName, request.getQuery(), filters, limit, offset)
                        : openSearchAdapter.bm25Search(indexName, request.getQuery(), filters, limit);
                case SEMANTIC -> {
                    float[] queryVector = embeddingProvider.generateEmbedding(request.getQuery());
                    yield offset > 0
                            ? openSearchAdapter.vectorSearch(indexName, queryVector, filters, limit, offset)
                            : openSearchAdapter.vectorSearch(indexName, queryVector, filters, limit);
                }
                case HYBRID -> {
                    float[] queryVector = embeddingProvider.generateEmbedding(request.getQuery());
                    yield offset > 0
                            ? openSearchAdapter.hybridSearch(indexName, request.getQuery(), queryVector, filters, limit, offset)
                            : openSearchAdapter.hybridSearch(indexName, request.getQuery(), queryVector, filters, limit);
                }
            };

            long tookMs = System.currentTimeMillis() - startTime;
            log.info("Arama tamamlandı — tip={}, sorgu='{}', sonuçlar={}, süre={}ms",
                    searchType, request.getQuery(), results.size(), tookMs);

            SearchResponse response = new SearchResponse(results, results.size(), tookMs, searchType);
            queryLogService.logStandardSearch(request, response, indexName);
            return response;
        } catch (Exception e) {
            queryLogService.logStandardError(request, indexName, e);
            throw e;
        }
    }

    /**
     * Hibrit aramanın tüm aşamalarını (BM25, Semantik Vektör, RRF/Score Füzyonu, Reranker)
     * eşzamanlı çalıştırıp her aşamanın katkısını açıklayan analiz metodu.
     *
     * @param request Hibrit açıklama isteği
     * @return Detaylı aşama dökümünü içeren HybridExplainResponse
     */
    public HybridExplainResponse explainHybrid(HybridExplainRequest request) {
        long startedAt = System.currentTimeMillis();
        String indexName = resolveIndexName(request.getIndexName());
        openSearchAdapter.createIndexIfNotExists(indexName);
        try {
            int limit = resolveLimit(request.getLimit());
            int multiplier = request.getCandidateMultiplier() == null ? 3 : request.getCandidateMultiplier();
            int candidateLimit = Math.min(limit * multiplier, searchProperties.getMaxLimit());
            int rankConstant = request.getRankConstant() == null ? 60 : request.getRankConstant();
            Map<String, Object> filters = buildFilters(request);

            double requestedBm25Weight = request.getBm25Weight() == null ? 0.5 : request.getBm25Weight();
            double requestedSemanticWeight = request.getSemanticWeight() == null ? 0.5 : request.getSemanticWeight();
            double weightTotal = requestedBm25Weight + requestedSemanticWeight;
            double bm25Weight = requestedBm25Weight / weightTotal;
            double semanticWeight = requestedSemanticWeight / weightTotal;

            // 1. BM25 aramasını asenkron başlat
            java.util.concurrent.CompletableFuture<Bm25ExecutionResult> bm25Future =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        long start = System.currentTimeMillis();
                        List<SearchResult> hits = openSearchAdapter.bm25Search(
                                indexName, request.getQuery(), filters, candidateLimit);
                        return new Bm25ExecutionResult(hits, System.currentTimeMillis() - start);
                    });

            long semanticStartedAt = System.currentTimeMillis();
            float[] queryVector = embeddingProvider.generateEmbedding(request.getQuery());
            List<SearchResult> semanticResults = openSearchAdapter.vectorSearch(
                    indexName, queryVector, filters, candidateLimit);
            List<HybridExplainResponse.RankedResult> rankedSemantic = rank(semanticResults);
            long semanticTookMs = System.currentTimeMillis() - semanticStartedAt;

            // BM25 sonucunu bekle
            Bm25ExecutionResult bm25Exec = bm25Future.join();
            List<SearchResult> bm25Results = bm25Exec.results();
            long bm25TookMs = bm25Exec.tookMs();

            String fusionMode = request.getFusionMode() == null ? "RRF" : request.getFusionMode();

            List<HybridExplainResponse.RankedResult> rankedBm25 = rank(bm25Results);
            List<HybridExplainResponse.FusionResult> finalResults = fuse(
                    bm25Results, semanticResults, bm25Weight, semanticWeight, rankConstant, limit, fusionMode);

            var settings = new HybridExplainResponse.HybridSettings(
                    limit, candidateLimit, multiplier, rankConstant, bm25Weight, semanticWeight,
                    request.getTypes() == null ? List.of() : List.copyOf(request.getTypes()),
                    new LinkedHashMap<>(filters), fusionMode);

            int totalCandidates = (int) java.util.stream.Stream.concat(
                            bm25Results.stream(), semanticResults.stream())
                    .map(SearchResult::getId).distinct().count();

            var result = new HybridExplainResponse(
                    request.getQuery(), indexName, System.currentTimeMillis() - startedAt, settings,
                    new HybridExplainResponse.SearchStage("BM25", bm25TookMs, rankedBm25),
                    new HybridExplainResponse.SearchStage("SEMANTIC", semanticTookMs, rankedSemantic),
                    finalResults, totalCandidates,
                    buildRerankStage(request.getQuery(), finalResults, limit));

            queryLogService.logSuccess(request, result);
            return result;
        } catch (Exception e) {
            queryLogService.logError(request, indexName, e);
            throw e;
        }
    }

    /**
     * Cross-Encoder reranker etkin ise aday füzyon sonuçlarını ikinci aşama puanlamaya tabi tutar.
     *
     * @param query Kullanıcı sorgusu
     * @param candidates Füzyon adayları
     * @param topN Dönecek sonuç adedi
     * @return RerankStage sonucu veya servis kapalıysa null
     */
    private HybridExplainResponse.RerankStage buildRerankStage(
            String query, List<HybridExplainResponse.FusionResult> candidates, int topN) {
        return rerankingService.filter(RerankingService::isAvailable).map(svc -> {
            long start = System.currentTimeMillis();
            List<RerankingService.RerankedResult> reranked = svc.rerank(query, candidates, topN);
            boolean fallback = reranked.stream().anyMatch(r -> r.relevanceScore() == r.result().rrfScore());

            List<HybridExplainResponse.RerankedFusionResult> results = new ArrayList<>();
            for (int i = 0; i < reranked.size(); i++) {
                RerankingService.RerankedResult r = reranked.get(i);
                results.add(new HybridExplainResponse.RerankedFusionResult(
                        i + 1,
                        r.relevanceScore(),
                        r.result().rrfScore(),
                        r.result().bm25Rank(),
                        r.result().semanticRank(),
                        r.result().document()));
            }
            return new HybridExplainResponse.RerankStage(
                    svc.getClass().getSimpleName(),
                    System.currentTimeMillis() - start,
                    fallback, results);
        }).orElse(null);
    }

    /**
     * Liste elemanlarına sıra numarası (1..N) vererek RankedResult listesine dönüştürür.
     *
     * @param results Ham arama sonuçları
     * @return Sıralanmış sonuçlar
     */
    private List<HybridExplainResponse.RankedResult> rank(List<SearchResult> results) {
        return java.util.stream.IntStream.range(0, results.size())
                .mapToObj(index -> new HybridExplainResponse.RankedResult(
                        index + 1, results.get(index).getScore(), results.get(index)))
                .toList();
    }

    /**
     * BM25 ve Semantik sonuçları RRF veya normalize puan füzyonu ile birleştirir.
     *
     * @param bm25Results BM25 sonuçları
     * @param semanticResults Semantik sonuçları
     * @param bm25Weight BM25 ağırlık katsayısı
     * @param semanticWeight Semantik ağırlık katsayısı
     * @param rankConstant RRF formül sabiti (k=60)
     * @param limit Sonuç limiti
     * @param fusionMode Füzyon modu ("RRF" veya "SCORE")
     * @return Birleştirilmiş füzyon sonuçları listesi
     */
    private List<HybridExplainResponse.FusionResult> fuse(
            List<SearchResult> bm25Results, List<SearchResult> semanticResults,
            double bm25Weight, double semanticWeight, int rankConstant, int limit,
            String fusionMode) {
        Map<String, FusionCandidate> candidates = new LinkedHashMap<>();
        boolean isRrf = "RRF".equalsIgnoreCase(fusionMode);

        double maxBm25 = bm25Results.stream().mapToDouble(SearchResult::getScore).max().orElse(0.0);
        double maxSemantic = semanticResults.stream().mapToDouble(SearchResult::getScore).max().orElse(0.0);

        for (int index = 0; index < bm25Results.size(); index++) {
            SearchResult result = bm25Results.get(index);
            FusionCandidate candidate = candidates.computeIfAbsent(
                    result.getId(), ignored -> new FusionCandidate(result));
            candidate.bm25Rank = index + 1;
            if (isRrf) {
                candidate.bm25Contribution = bm25Weight / (rankConstant + index + 1.0);
            } else {
                candidate.normalizedBm25 = maxBm25 > 0 ? Math.max(0.0, result.getScore()) / maxBm25 : 0.0;
                candidate.bm25Contribution = bm25Weight * candidate.normalizedBm25;
            }
        }
        for (int index = 0; index < semanticResults.size(); index++) {
            SearchResult result = semanticResults.get(index);
            FusionCandidate candidate = candidates.computeIfAbsent(
                    result.getId(), ignored -> new FusionCandidate(result));
            candidate.semanticRank = index + 1;
            if (isRrf) {
                candidate.semanticContribution = semanticWeight / (rankConstant + index + 1.0);
            } else {
                candidate.normalizedSemantic = maxSemantic > 0 ? Math.max(0.0, result.getScore()) / maxSemantic : 0.0;
                candidate.semanticContribution = semanticWeight * candidate.normalizedSemantic;
            }
        }

        List<FusionCandidate> sorted = candidates.values().stream()
                .sorted(Comparator.comparingDouble(FusionCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.document.getId()))
                .limit(limit)
                .toList();

        return java.util.stream.IntStream.range(0, sorted.size())
                .mapToObj(index -> {
                    FusionCandidate candidate = sorted.get(index);
                    return new HybridExplainResponse.FusionResult(
                            index + 1, candidate.score(), candidate.bm25Rank, candidate.semanticRank,
                            candidate.bm25Contribution, candidate.semanticContribution, candidate.document,
                            candidate.normalizedBm25, candidate.normalizedSemantic);
                })
                .toList();
    }

    /**
     * Füzyon adayı ara veri yapısı.
     */
    private static final class FusionCandidate {
        private final SearchResult document;
        private Integer bm25Rank;
        private Integer semanticRank;
        private double bm25Contribution;
        private double semanticContribution;
        private Double normalizedBm25;
        private Double normalizedSemantic;

        private FusionCandidate(SearchResult document) { this.document = document; }
        private double score() { return bm25Contribution + semanticContribution; }
    }

    /**
     * Belirtilen indeks adını çözer veya sistem varsayılanını atar.
     *
     * @param indexName İstekle gelen indeks adı
     * @return Çözümlenen indeks adı
     */
    private String resolveIndexName(String indexName) {
        return (indexName != null && !indexName.isBlank())
                ? indexName
                : searchProperties.getDefaultIndexName();
    }

    /**
     * İstenen sonuç adedini sistem limitleri dahilinde doğrular.
     *
     * @param limit İstenen limit
     * @return Geçerli limit
     */
    private int resolveLimit(Integer limit) {
        if (limit == null) return searchProperties.getLimit();
        return Math.min(limit, searchProperties.getMaxLimit());
    }

    /**
     * Arama tipini çözer veya varsayılanı atar.
     *
     * @param searchType İstek tipi
     * @return Çözümlenmiş SearchType
     */
    private SearchType resolveSearchType(SearchType searchType) {
        if (searchType != null) return searchType;
        try {
            return SearchType.valueOf(searchProperties.getDefaultSearchType());
        } catch (IllegalArgumentException e) {
            return SearchType.HYBRID;
        }
    }

    /**
     * Arama isteğindeki filtreleri ve tür (type) kriterlerini harita olarak hazırlar.
     *
     * @param request Arama isteği
     * @return Filtre kriterleri haritası
     */
    private Map<String, Object> buildFilters(SearchRequest request) {
        Map<String, Object> filters = new HashMap<>();
        if (request.getFilters() != null) {
            filters.putAll(request.getFilters());
        }
        if (request.getTypes() != null && !request.getTypes().isEmpty()) {
            filters.put("type", request.getTypes());
        }
        return filters;
    }

    /**
     * Asenkron BM25 çalıştırma sonucunu taşıyan dahili kayıt.
     *
     * @param results BM25 arama sonuçları
     * @param tookMs Geçen süre (ms)
     */
    private record Bm25ExecutionResult(List<SearchResult> results, long tookMs) {}
}
