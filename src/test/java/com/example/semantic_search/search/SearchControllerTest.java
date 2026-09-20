package com.example.semantic_search.search;

import com.example.semantic_search.exception.GlobalExceptionHandler;
import com.example.semantic_search.exception.OpenSearchUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SearchController.class, GlobalExceptionHandler.class})
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchQueryService searchQueryService;

    @Test
    void search_shouldReturnResults() throws Exception {
        SearchResult result = new SearchResult();
        result.setId("doc-1");
        result.setTitle("Radar Alpha");
        result.setScore(5.0);

        SearchResponse response = new SearchResponse(
                List.of(result), 1, 42, SearchType.BM25);

        when(searchQueryService.search(any())).thenReturn(response);

        String body = """
                {
                    "query": "radar station",
                    "searchType": "BM25",
                    "limit": 10
                }
                """;

        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value("doc-1"))
                .andExpect(jsonPath("$.results[0].title").value("Radar Alpha"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.searchType").value("BM25"));
    }

    @Test
    void search_shouldReturn400WhenQueryIsBlank() throws Exception {
        String body = """
                {
                    "query": "",
                    "searchType": "BM25"
                }
                """;

        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    void search_shouldReturn400WhenQueryIsMissing() throws Exception {
        String body = """
                {
                    "searchType": "BM25"
                }
                """;

        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_shouldReturn503WhenOpenSearchUnavailable() throws Exception {
        when(searchQueryService.search(any()))
                .thenThrow(new OpenSearchUnavailableException("Connection refused"));

        String body = """
                {
                    "query": "test query",
                    "searchType": "BM25"
                }
                """;

        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service Unavailable"));
    }

    @Test
    void browserRequestToSearch_shouldReturn405WithAllowedMethod() throws Exception {
        mockMvc.perform(get("/api/v1/search"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void rootWithoutWebInterface_shouldReturn404() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void unknownRoute_shouldReturn404() throws Exception {
        mockMvc.perform(get("/unknown-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void search_shouldReturn400ForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Request is malformed or contains invalid values."));
    }

    @Test
    void search_shouldReturn400ForInvalidSearchType() throws Exception {
        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"radar\",\"searchType\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void search_shouldReturn415ForUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("radar"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void search_shouldKeepUnexpectedFailuresAs500() throws Exception {
        when(searchQueryService.search(any()))
                .thenThrow(new IllegalStateException("Internal implementation detail"));

        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"radar\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred. Please try again later."));
    }

    @Test
    void explainHybrid_shouldReturnEveryRankingStage() throws Exception {
        SearchResult result = new SearchResult();
        result.setId("doc-1");
        result.setTitle("Radar Alpha");
        result.setScore(4.2);

        HybridExplainResponse response = new HybridExplainResponse(
                "radar", "entities", 31,
                new HybridExplainResponse.HybridSettings(
                        10, 30, 3, 60, 0.4, 0.6, List.of(), Map.of()),
                new HybridExplainResponse.SearchStage("BM25", 5,
                        List.of(new HybridExplainResponse.RankedResult(1, 4.2, result))),
                new HybridExplainResponse.SearchStage("SEMANTIC", 8,
                        List.of(new HybridExplainResponse.RankedResult(1, 0.91, result))),
                List.of(new HybridExplainResponse.FusionResult(
                        1, 0.01639, 1, 1, 0.00656, 0.00984, result)),
                1);
        when(searchQueryService.explainHybrid(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/search/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"radar","bm25Weight":0.4,"semanticWeight":0.6}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bm25.results[0].rank").value(1))
                .andExpect(jsonPath("$.semantic.results[0].originalScore").value(0.91))
                .andExpect(jsonPath("$.finalResults[0].bm25Contribution").value(0.00656))
                .andExpect(jsonPath("$.finalResults[0].semanticRank").value(1));
    }

    @Test
    void explainHybrid_shouldRejectTwoZeroWeights() throws Exception {
        mockMvc.perform(post("/api/v1/search/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"radar","bm25Weight":0,"semanticWeight":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }
}
