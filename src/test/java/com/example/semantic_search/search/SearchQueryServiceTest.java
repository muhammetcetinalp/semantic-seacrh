package com.example.semantic_search.search;

import com.example.semantic_search.configuration.SearchProperties;
import com.example.semantic_search.embedding.EmbeddingProvider;
import com.example.semantic_search.opensearch.OpenSearchAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchQueryServiceTest {

    @Mock
    private OpenSearchAdapter openSearchAdapter;

    @Mock
    private EmbeddingProvider embeddingProvider;

    @Mock
    private SearchQueryLogService searchQueryLogService;

    private SearchProperties searchProperties;
    private SearchQueryService searchQueryService;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.setLimit(20);
        searchProperties.setMaxLimit(100);
        searchProperties.setDefaultSearchType("HYBRID");
        searchProperties.setDefaultIndexName("entities");

        searchQueryService = new SearchQueryService(
                openSearchAdapter, embeddingProvider, searchProperties,
                searchQueryLogService, java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty());
    }

    @Test
    void bm25Search_shouldNotGenerateEmbedding() {
        SearchRequest request = new SearchRequest();
        request.setQuery("radar station");
        request.setSearchType(SearchType.BM25);

        SearchResult result = new SearchResult();
        result.setId("doc-1");
        result.setTitle("Radar Station Alpha");
        result.setScore(5.2);

        when(openSearchAdapter.bm25Search(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of(result));

        SearchResponse response = searchQueryService.search(request);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getSearchType()).isEqualTo(SearchType.BM25);
        verify(embeddingProvider, never()).generateEmbedding(anyString());
    }

    @Test
    void semanticSearch_shouldGenerateEmbedding() {
        SearchRequest request = new SearchRequest();
        request.setQuery("communication problems");
        request.setSearchType(SearchType.SEMANTIC);

        float[] mockVector = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingProvider.generateEmbedding("communication problems")).thenReturn(mockVector);
        when(openSearchAdapter.vectorSearch(anyString(), eq(mockVector), any(), anyInt()))
                .thenReturn(List.of());

        SearchResponse response = searchQueryService.search(request);

        assertThat(response.getSearchType()).isEqualTo(SearchType.SEMANTIC);
        verify(embeddingProvider).generateEmbedding("communication problems");
    }

    @Test
    void hybridSearch_shouldUseBothBm25AndVector() {
        SearchRequest request = new SearchRequest();
        request.setQuery("surveillance unit");
        request.setSearchType(SearchType.HYBRID);

        float[] mockVector = new float[]{0.4f, 0.5f, 0.6f};
        when(embeddingProvider.generateEmbedding("surveillance unit")).thenReturn(mockVector);
        when(openSearchAdapter.hybridSearch(anyString(), eq("surveillance unit"),
                eq(mockVector), any(), anyInt()))
                .thenReturn(List.of());

        SearchResponse response = searchQueryService.search(request);

        assertThat(response.getSearchType()).isEqualTo(SearchType.HYBRID);
        verify(embeddingProvider).generateEmbedding("surveillance unit");
        verify(openSearchAdapter).hybridSearch(anyString(), eq("surveillance unit"),
                eq(mockVector), any(), anyInt());
    }

    @Test
    void search_shouldApplyTypeFilter() {
        SearchRequest request = new SearchRequest();
        request.setQuery("test");
        request.setSearchType(SearchType.BM25);
        request.setTypes(List.of("unit", "event"));

        when(openSearchAdapter.bm25Search(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of());

        searchQueryService.search(request);

        verify(openSearchAdapter).bm25Search(
                eq("entities"), eq("test"),
                eq(Map.of("type", List.of("unit", "event"))), eq(20));
    }

    @Test
    void search_shouldApplyStructuredFilters() {
        SearchRequest request = new SearchRequest();
        request.setQuery("test");
        request.setSearchType(SearchType.BM25);
        request.setFilters(Map.of("region", "ANKARA", "status", "ACTIVE"));

        when(openSearchAdapter.bm25Search(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of());

        searchQueryService.search(request);

        verify(openSearchAdapter).bm25Search(
                eq("entities"), eq("test"),
                eq(Map.of("region", "ANKARA", "status", "ACTIVE")), eq(20));
    }

    @Test
    void search_shouldUseDefaultSearchTypeWhenNotSpecified() {
        SearchRequest request = new SearchRequest();
        request.setQuery("test");

        float[] mockVector = new float[]{0.1f};
        when(embeddingProvider.generateEmbedding(anyString())).thenReturn(mockVector);
        when(openSearchAdapter.hybridSearch(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of());

        SearchResponse response = searchQueryService.search(request);

        assertThat(response.getSearchType()).isEqualTo(SearchType.HYBRID);
    }

    @Test
    void search_shouldRespectLimitFromRequest() {
        SearchRequest request = new SearchRequest();
        request.setQuery("test");
        request.setSearchType(SearchType.BM25);
        request.setLimit(5);

        when(openSearchAdapter.bm25Search(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of());

        searchQueryService.search(request);

        verify(openSearchAdapter).bm25Search(anyString(), anyString(), any(), eq(5));
    }

    @Test
    void search_shouldCapLimitAtMaxLimit() {
        SearchRequest request = new SearchRequest();
        request.setQuery("test");
        request.setSearchType(SearchType.BM25);
        request.setLimit(500);

        when(openSearchAdapter.bm25Search(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of());

        searchQueryService.search(request);

        verify(openSearchAdapter).bm25Search(anyString(), anyString(), any(), eq(100));
    }

    @Test
    void explainHybrid_shouldExposeBothRankingsAndWeightedRrfCalculation() {
        HybridExplainRequest request = new HybridExplainRequest();
        request.setQuery("uzun menzilli gözetleme");
        request.setLimit(3);
        request.setCandidateMultiplier(2);
        request.setRankConstant(10);
        request.setBm25Weight(0.25);
        request.setSemanticWeight(0.75);
        request.setFilters(Map.of("region", "ANKARA"));

        SearchResult a = result("a", "Radar Alpha", 8.4);
        SearchResult b = result("b", "Gözetleme Birimi", 6.1);
        SearchResult semanticB = result("b", "Gözetleme Birimi", 0.96);
        SearchResult c = result("c", "Erken Uyarı", 0.88);
        float[] vector = new float[]{0.1f, 0.2f};

        when(openSearchAdapter.bm25Search("entities", request.getQuery(), request.getFilters(), 6))
                .thenReturn(List.of(a, b));
        when(embeddingProvider.generateEmbedding(request.getQuery())).thenReturn(vector);
        when(openSearchAdapter.vectorSearch("entities", vector, request.getFilters(), 6))
                .thenReturn(List.of(semanticB, c));

        HybridExplainResponse response = searchQueryService.explainHybrid(request);

        assertThat(response.settings().candidateLimit()).isEqualTo(6);
        assertThat(response.settings().bm25Weight()).isEqualTo(0.25);
        assertThat(response.settings().semanticWeight()).isEqualTo(0.75);
        assertThat(response.bm25().results()).extracting(HybridExplainResponse.RankedResult::rank)
                .containsExactly(1, 2);
        assertThat(response.bm25().results()).extracting(item -> item.document().getId())
                .containsExactly("a", "b");
        assertThat(response.semantic().results()).extracting(item -> item.document().getId())
                .containsExactly("b", "c");
        assertThat(response.finalResults()).extracting(item -> item.document().getId())
                .containsExactly("b", "c", "a");
        assertThat(response.finalResults().getFirst().bm25Rank()).isEqualTo(2);
        assertThat(response.finalResults().getFirst().semanticRank()).isEqualTo(1);
        assertThat(response.finalResults().getFirst().bm25Contribution())
                .isCloseTo(0.25 / 12, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(response.finalResults().getFirst().semanticContribution())
                .isCloseTo(0.75 / 11, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(response.totalCandidates()).isEqualTo(3);
    }

    @Test
    void explainHybrid_shouldNormalizeWeights() {
        HybridExplainRequest request = new HybridExplainRequest();
        request.setQuery("radar");
        request.setBm25Weight(0.2);
        request.setSemanticWeight(0.6);
        when(openSearchAdapter.bm25Search(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of());
        when(embeddingProvider.generateEmbedding(anyString())).thenReturn(new float[]{0.1f});
        when(openSearchAdapter.vectorSearch(anyString(), any(), any(), anyInt()))
                .thenReturn(List.of());

        HybridExplainResponse response = searchQueryService.explainHybrid(request);

        assertThat(response.settings().bm25Weight()).isEqualTo(0.25);
        assertThat(response.settings().semanticWeight()).isEqualTo(0.75);
    }

    @Test
    void search_shouldPassOffsetToOpenSearchAdapter() {
        SearchRequest request = new SearchRequest();
        request.setQuery("yangin");
        request.setSearchType(SearchType.BM25);
        request.setLimit(10);
        request.setOffset(20);

        when(openSearchAdapter.bm25Search(anyString(), eq("yangin"), any(), eq(10), eq(20)))
                .thenReturn(List.of());

        searchQueryService.search(request);

        verify(openSearchAdapter).bm25Search(eq("entities"), eq("yangin"), any(), eq(10), eq(20));
        verify(searchQueryLogService).logStandardSearch(eq(request), any(), eq("entities"));
    }

    @Test
    void search_shouldLogStandardSearchOnSuccess() {
        SearchRequest request = new SearchRequest();
        request.setQuery("kaza");
        request.setSearchType(SearchType.BM25);

        when(openSearchAdapter.bm25Search(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of());

        searchQueryService.search(request);

        verify(searchQueryLogService).logStandardSearch(eq(request), any(), eq("entities"));
    }

    private SearchResult result(String id, String title, double score) {
        SearchResult result = new SearchResult();
        result.setId(id);
        result.setTitle(title);
        result.setScore(score);
        return result;
    }
}
