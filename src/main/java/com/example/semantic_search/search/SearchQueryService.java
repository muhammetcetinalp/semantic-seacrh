package com.example.semantic_search.search;

import com.example.semantic_search.configuration.ColbertProperties;
import com.example.semantic_search.configuration.SearchProperties;
import com.example.semantic_search.embedding.EmbeddingProvider;
import com.example.semantic_search.opensearch.OpenSearchAdapter;
import com.example.semantic_search.qdrant.QdrantAdapter;
import com.example.semantic_search.qdrant.QdrantSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class SearchQueryService {

    private static final Logger log = LoggerFactory.getLogger(SearchQueryService.class);

    private final OpenSearchAdapter openSearchAdapter;
    private final EmbeddingProvider embeddingProvider;
    private final SearchProperties searchProperties;
    private final SearchQueryLogService queryLogService;
    private final Optional<RerankingService> rerankingService;
    private final Optional<ColbertService> colbertService;
    private final Optional<QdrantAdapter> qdrantAdapter;
    private final Optional<ColbertProperties> colbertProperties;

    @Autowired
    public SearchQueryService(OpenSearchAdapter openSearchAdapter,
                              EmbeddingProvider embeddingProvider,
                              SearchProperties searchProperties,
                              SearchQueryLogService queryLogService,
                              Optional<RerankingService> rerankingService,
                              Optional<ColbertService> colbertService,
                              Optional<QdrantAdapter> qdrantAdapter,
                              Optional<ColbertProperties> colbertProperties) {
        this.openSearchAdapter = openSearchAdapter;
        this.embeddingProvider = embeddingProvider;
        this.searchProperties = searchProperties;
        this.queryLogService = queryLogService;
        this.rerankingService = rerankingService;
        this.colbertService = colbertService != null ? colbertService : Optional.empty();
        this.qdrantAdapter = qdrantAdapter != null ? qdrantAdapter : Optional.empty();
        this.colbertProperties = colbertProperties != null ? colbertProperties : Optional.empty();
        rerankingService.ifPresent(r -> log.info("Reranking enabled — {}", r.getClass().getSimpleName()));
        this.colbertService.ifPresent(c -> log.info("ColBERT search integration available"));
        this.qdrantAdapter.ifPresent(q -> log.info("Qdrant multi-vector store integration available"));
    }

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
            log.info("Search completed — type={}, query='{}', results={}, took={}ms",
                    searchType, request.getQuery(), results.size(), tookMs);

            SearchResponse response = new SearchResponse(results, results.size(), tookMs, searchType);
            queryLogService.logStandardSearch(request, response, indexName);
            return response;
        } catch (Exception e) {
            queryLogService.logStandardError(request, indexName, e);
            throw e;
        }
    }

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

            // 1. Kick off BM25 search concurrently
            java.util.concurrent.CompletableFuture<Bm25ExecutionResult> bm25Future =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        long start = System.currentTimeMillis();
                        List<SearchResult> hits = openSearchAdapter.bm25Search(
                                indexName, request.getQuery(), filters, candidateLimit);
                        return new Bm25ExecutionResult(hits, System.currentTimeMillis() - start);
                    });

            boolean useColbert = "COLBERT".equalsIgnoreCase(request.getSemanticMode())
                    && colbertService.filter(ColbertService::isAvailable).isPresent();

            long semanticStartedAt = System.currentTimeMillis();
            List<SearchResult> semanticResults;
            List<HybridExplainResponse.RankedResult> rankedSemantic;
            String semanticStageName;

            if (useColbert) {
                semanticStageName = "COLBERT";
                boolean useQdrant = colbertProperties.map(p -> "qdrant".equalsIgnoreCase(p.getStorage())).orElse(false)
                        && qdrantAdapter.filter(QdrantAdapter::isAvailable).isPresent();

                List<SearchResult> colbertDocs = null;
                List<HybridExplainResponse.RankedResult> colbertRanked = null;

                if (useQdrant) {
                    // 1. FAST PATH (Qdrant Multi-Vector Store): Only embed the query! No document re-embeddings!
                    List<List<Float>> queryVectors = colbertService.get().embedQuery(request.getQuery());
                    if (queryVectors != null && !queryVectors.isEmpty()) {
                        List<QdrantSearchResult> qdrantHits = qdrantAdapter.get().searchMaxSim(queryVectors, filters, candidateLimit);
                        if (qdrantHits != null && !qdrantHits.isEmpty()) {
                            log.info("ColBERT search executed via Qdrant MaxSim — returned {} hits", qdrantHits.size());

                            List<String> hitIds = qdrantHits.stream()
                                    .map(QdrantSearchResult::entityId)
                                    .filter(Objects::nonNull)
                                    .toList();

                            List<SearchResult> osDocs = openSearchAdapter.getDocumentsByIds(indexName, hitIds);
                            Map<String, SearchResult> docMap = new HashMap<>();
                            for (SearchResult c : osDocs) {
                                docMap.put(c.getId(), c);
                            }

                            List<SearchResult> orderedDocs = new ArrayList<>();
                            List<HybridExplainResponse.RankedResult> ranked = new ArrayList<>();

                            for (int i = 0; i < qdrantHits.size(); i++) {
                                QdrantSearchResult hit = qdrantHits.get(i);
                                SearchResult doc = docMap.get(hit.entityId());
                                if (doc == null) {
                                    try {
                                        Map<String, Object> single = openSearchAdapter.getDocument(indexName, hit.entityId());
                                        if (single != null) {
                                            doc = openSearchAdapter.mapSource(single);
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                                if (doc == null) {
                                    doc = new SearchResult();
                                    doc.setId(hit.entityId());
                                    doc.setTitle((String) hit.payload().getOrDefault("title", ""));
                                    doc.setSearchText((String) hit.payload().getOrDefault("searchText", ""));
                                    doc.setShortText((String) hit.payload().getOrDefault("shortText", ""));
                                    doc.setLongText((String) hit.payload().getOrDefault("longText", ""));
                                    doc.setBirim((String) hit.payload().getOrDefault("birim", ""));
                                    doc.setAdres((String) hit.payload().getOrDefault("adres", ""));
                                    doc.setTarih((String) hit.payload().getOrDefault("tarih", ""));
                                    doc.setType((String) hit.payload().getOrDefault("type", ""));
                                }
                                doc.setScore(hit.score());
                                orderedDocs.add(doc);
                                List<HybridExplainResponse.TokenMatch> tokenMatches = List.of();
                                if (colbertService.isPresent() && (doc.getSearchText() != null || doc.getTitle() != null)) {
                                    tokenMatches = colbertService.get().computeTokenMatches(
                                            request.getQuery(), doc.getTitle(), doc.getSearchText());
                                }
                                ranked.add(new HybridExplainResponse.RankedResult(
                                         i + 1, hit.score(), doc, tokenMatches
                                ));
                            }
                            colbertDocs = orderedDocs;
                            colbertRanked = ranked;
                        }
                    }
                }

                // Fallback to on-the-fly candidate scoring if Qdrant returned no results (e.g. not synced yet)
                // Prioritize BM25 keyword candidates for the query over arbitrary match_all documents
                if (colbertDocs == null) {
                    List<SearchResult> bm25Candidates = bm25Future.join().results();
                    List<SearchResult> candidatePool = !bm25Candidates.isEmpty()
                            ? bm25Candidates
                            : openSearchAdapter.getCandidates(indexName, filters, candidateLimit * 2);
                    var colbertRank = colbertService.get().scoreAndRank(request.getQuery(), candidatePool, candidateLimit);
                    colbertDocs = colbertRank.orderedDocuments();
                    colbertRanked = colbertRank.rankedResults();
                }

                semanticResults = colbertDocs;
                rankedSemantic = colbertRanked;
            } else {
                semanticStageName = "SEMANTIC";
                float[] queryVector = embeddingProvider.generateEmbedding(request.getQuery());
                semanticResults = openSearchAdapter.vectorSearch(
                        indexName, queryVector, filters, candidateLimit);
                rankedSemantic = rank(semanticResults);
            }
            long semanticTookMs = System.currentTimeMillis() - semanticStartedAt;

            // Wait for BM25 result
            Bm25ExecutionResult bm25Exec = bm25Future.join();
            List<SearchResult> bm25Results = bm25Exec.results();
            long bm25TookMs = bm25Exec.tookMs();

            List<HybridExplainResponse.RankedResult> rankedBm25 = rank(bm25Results);
            List<HybridExplainResponse.FusionResult> finalResults = fuse(
                    bm25Results, semanticResults, bm25Weight, semanticWeight, rankConstant, limit);

            var settings = new HybridExplainResponse.HybridSettings(
                    limit, candidateLimit, multiplier, rankConstant, bm25Weight, semanticWeight,
                    request.getTypes() == null ? List.of() : List.copyOf(request.getTypes()),
                    new LinkedHashMap<>(filters));

            int totalCandidates = (int) java.util.stream.Stream.concat(
                            bm25Results.stream(), semanticResults.stream())
                    .map(SearchResult::getId).distinct().count();

            var result = new HybridExplainResponse(
                    request.getQuery(), indexName, System.currentTimeMillis() - startedAt, settings,
                    new HybridExplainResponse.SearchStage("BM25", bm25TookMs, rankedBm25),
                    new HybridExplainResponse.SearchStage(semanticStageName, semanticTookMs, rankedSemantic),
                    finalResults, totalCandidates,
                    buildRerankStage(request.getQuery(), finalResults, limit));

            queryLogService.logSuccess(request, result);
            return result;
        } catch (Exception e) {
            queryLogService.logError(request, indexName, e);
            throw e;
        }
    }

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

    private List<HybridExplainResponse.RankedResult> rank(List<SearchResult> results) {
        return java.util.stream.IntStream.range(0, results.size())
                .mapToObj(index -> new HybridExplainResponse.RankedResult(
                        index + 1, results.get(index).getScore(), results.get(index)))
                .toList();
    }

    private List<HybridExplainResponse.FusionResult> fuse(
            List<SearchResult> bm25Results, List<SearchResult> semanticResults,
            double bm25Weight, double semanticWeight, int rankConstant, int limit) {
        Map<String, FusionCandidate> candidates = new LinkedHashMap<>();

        for (int index = 0; index < bm25Results.size(); index++) {
            SearchResult result = bm25Results.get(index);
            FusionCandidate candidate = candidates.computeIfAbsent(
                    result.getId(), ignored -> new FusionCandidate(result));
            candidate.bm25Rank = index + 1;
            candidate.bm25Contribution = bm25Weight / (rankConstant + index + 1.0);
        }
        for (int index = 0; index < semanticResults.size(); index++) {
            SearchResult result = semanticResults.get(index);
            FusionCandidate candidate = candidates.computeIfAbsent(
                    result.getId(), ignored -> new FusionCandidate(result));
            candidate.semanticRank = index + 1;
            candidate.semanticContribution = semanticWeight / (rankConstant + index + 1.0);
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
                            candidate.bm25Contribution, candidate.semanticContribution, candidate.document);
                })
                .toList();
    }

    private static final class FusionCandidate {
        private final SearchResult document;
        private Integer bm25Rank;
        private Integer semanticRank;
        private double bm25Contribution;
        private double semanticContribution;

        private FusionCandidate(SearchResult document) { this.document = document; }
        private double score() { return bm25Contribution + semanticContribution; }
    }

    private String resolveIndexName(String indexName) {
        return (indexName != null && !indexName.isBlank())
                ? indexName
                : searchProperties.getDefaultIndexName();
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) return searchProperties.getLimit();
        return Math.min(limit, searchProperties.getMaxLimit());
    }

    private SearchType resolveSearchType(SearchType searchType) {
        if (searchType != null) return searchType;
        try {
            return SearchType.valueOf(searchProperties.getDefaultSearchType());
        } catch (IllegalArgumentException e) {
            return SearchType.HYBRID;
        }
    }

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

    private record Bm25ExecutionResult(List<SearchResult> results, long tookMs) {}
}
