package com.example.semantic_search.search;

import com.example.semantic_search.dto.HybridExplainResponse;
import com.example.semantic_search.dto.SearchResult;
import com.example.semantic_search.service.RerankingService;
import com.example.semantic_search.service.TeiRerankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TeiRerankingServiceTest {

    private MockRestServiceServer mockServer;
    private TeiRerankingService rerankingService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        rerankingService = new TeiRerankingService(builder, "http://localhost:8082", "BAAI/bge-reranker-v2-m3");
    }

    @Test
    void rerank_shouldCallTeiEndpointAndSortResultsDescending() {
        SearchResult doc1 = new SearchResult();
        doc1.setId("doc-1");
        doc1.setTitle("Tekne Kurtarma");
        doc1.setSearchText("Tekirdağ sahilinde tekne kurtarıldı");

        SearchResult doc2 = new SearchResult();
        doc2.setId("doc-2");
        doc2.setTitle("Radar Arızası");
        doc2.setSearchText("Samsun istasyonunda bakım yapıldı");

        HybridExplainResponse.FusionResult fr1 = new HybridExplainResponse.FusionResult(
                1, 0.016, 1, 1, 0.008, 0.008, doc1, 1.0, 0.95);
        HybridExplainResponse.FusionResult fr2 = new HybridExplainResponse.FusionResult(
                2, 0.008, 2, 2, 0.004, 0.004, doc2, 0.5, 0.40);

        String jsonResponse = """
                [
                  {"index": 0, "score": 0.9852},
                  {"index": 1, "score": 0.0210}
                ]
                """;

        mockServer.expect(requestTo("http://localhost:8082/rerank"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<RerankingService.RerankedResult> reranked = rerankingService.rerank(
                "Tekirdağ Tekne Kurtarma", List.of(fr1, fr2), 10);

        mockServer.verify();
        assertThat(reranked).hasSize(2);
        assertThat(reranked.get(0).result().document().getId()).isEqualTo("doc-1");
        assertThat(reranked.get(0).relevanceScore()).isEqualTo(0.9852);
        assertThat(reranked.get(1).result().document().getId()).isEqualTo("doc-2");
        assertThat(reranked.get(1).relevanceScore()).isEqualTo(0.021);
    }

    @Test
    void rerank_shouldSupportInfinityResponseFormat() {
        SearchResult doc1 = new SearchResult();
        doc1.setId("doc-1");
        doc1.setTitle("Tekne Kurtarma");

        SearchResult doc2 = new SearchResult();
        doc2.setId("doc-2");
        doc2.setTitle("Radar Arızası");

        HybridExplainResponse.FusionResult fr1 = new HybridExplainResponse.FusionResult(
                1, 0.016, 1, 1, 0.008, 0.008, doc1, 1.0, 0.95);
        HybridExplainResponse.FusionResult fr2 = new HybridExplainResponse.FusionResult(
                2, 0.008, 2, 2, 0.004, 0.004, doc2, 0.5, 0.40);

        String jsonResponse = """
                {
                  "object": "rerank",
                  "results": [
                    {"index": 0, "relevance_score": 0.9543},
                    {"index": 1, "relevance_score": 0.0412}
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:8082/rerank"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<RerankingService.RerankedResult> reranked = rerankingService.rerank(
                "Tekirdağ Tekne Kurtarma", List.of(fr1, fr2), 10);

        mockServer.verify();
        assertThat(reranked).hasSize(2);
        assertThat(reranked.get(0).result().document().getId()).isEqualTo("doc-1");
        assertThat(reranked.get(0).relevanceScore()).isEqualTo(0.9543);
        assertThat(reranked.get(1).result().document().getId()).isEqualTo("doc-2");
        assertThat(reranked.get(1).relevanceScore()).isEqualTo(0.0412);
    }

    @Test
    void rerank_whenTeiFails_shouldFallbackToFusionOrder() {
        SearchResult doc1 = new SearchResult();
        doc1.setId("doc-1");
        doc1.setTitle("Test Doc");

        HybridExplainResponse.FusionResult fr1 = new HybridExplainResponse.FusionResult(
                1, 0.016, 1, 1, 0.008, 0.008, doc1, 1.0, 0.95);

        mockServer.expect(requestTo("http://localhost:8082/rerank"))
                .andRespond(withServerError());

        List<RerankingService.RerankedResult> reranked = rerankingService.rerank(
                "test query", List.of(fr1), 10);

        // Fallback returns result with original fusion score without throwing exception
        assertThat(reranked).hasSize(1);
        assertThat(reranked.get(0).result().document().getId()).isEqualTo("doc-1");
        assertThat(reranked.get(0).relevanceScore()).isEqualTo(0.016);
    }

    @Test
    void isAvailable_shouldReturnTrueWhenHealthOk() {
        mockServer.expect(requestTo("http://localhost:8082/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess());

        assertThat(rerankingService.isAvailable()).isTrue();
        mockServer.verify();
    }

    @Test
    void isAvailable_shouldReturnFalseWhenHealthFails() {
        mockServer.expect(requestTo("http://localhost:8082/health"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("http://localhost:8082/"))
                .andRespond(withServerError());

        assertThat(rerankingService.isAvailable()).isFalse();
    }
}
