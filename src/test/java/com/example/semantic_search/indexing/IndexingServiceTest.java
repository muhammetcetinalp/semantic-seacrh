package com.example.semantic_search.indexing;

import com.example.semantic_search.configuration.SearchProperties;
import com.example.semantic_search.embedding.EmbeddingProvider;
import com.example.semantic_search.opensearch.OpenSearchAdapter;
import com.example.semantic_search.opensearch.OpenSearchDocumentSourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexingServiceTest {

    @Mock
    private OpenSearchAdapter openSearchAdapter;

    @Mock
    private EmbeddingProvider embeddingProvider;

    @Mock
    private IndexingStateRepository indexingStateRepository;

    private SearchProperties searchProperties;
    private IndexingService indexingService;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.setDefaultIndexName("entities");

        indexingService = new IndexingService(
                openSearchAdapter, embeddingProvider,
                indexingStateRepository, searchProperties,
                new OpenSearchDocumentSourceMapper(JsonMapper.builder().build()));
    }

    @Test
    void indexDocument_shouldGenerateEmbeddingAndIndex() {
        IndexDocumentRequest request = createRequest("doc-1", "unit", "Radar Alpha",
                "Long range radar surveillance unit");

        float[] embedding = {0.1f, 0.2f, 0.3f};
        when(embeddingProvider.generateEmbedding("Long range radar surveillance unit"))
                .thenReturn(embedding);
        when(indexingStateRepository.findByDocumentIdAndIndexName(anyString(), anyString()))
                .thenReturn(Optional.empty());

        indexingService.indexDocument(request);

        ArgumentCaptor<SearchDocument> captor = ArgumentCaptor.forClass(SearchDocument.class);
        verify(openSearchAdapter).indexDocument(captor.capture());
        verify(openSearchAdapter).createIndexIfNotExists("entities");

        SearchDocument indexed = captor.getValue();
        assertThat(indexed.getId()).isEqualTo("doc-1");
        assertThat(indexed.getType()).isEqualTo("unit");
        assertThat(indexed.getSearchText()).isEqualTo("Long range radar surveillance unit");
        assertThat(indexed.getEmbedding()).isEqualTo(embedding);

        ArgumentCaptor<IndexingState> stateCaptor = ArgumentCaptor.forClass(IndexingState.class);
        verify(indexingStateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getDocumentSource())
                .contains("\"id\":\"doc-1\"")
                .contains("\"searchText\":\"Long range radar surveillance unit\"")
                .contains("\"embedding\":[0.1,0.2,0.3]")
                .doesNotContain("indexName", "structuredFields");
    }

    @Test
    void indexDocument_shouldSkipEmbeddingWhenSearchTextIsNull() {
        IndexDocumentRequest request = createRequest("doc-2", "event", "Alert", null);

        when(indexingStateRepository.findByDocumentIdAndIndexName(anyString(), anyString()))
                .thenReturn(Optional.empty());

        indexingService.indexDocument(request);

        verify(embeddingProvider, never()).generateEmbedding(anyString());

        ArgumentCaptor<SearchDocument> captor = ArgumentCaptor.forClass(SearchDocument.class);
        verify(openSearchAdapter).indexDocument(captor.capture());
        assertThat(captor.getValue().getEmbedding()).isNull();
    }

    @Test
    void updateDocument_shouldRegenerateEmbeddingWhenSemanticContentChanged() {
        IndexingState existingState = new IndexingState();
        existingState.setDocumentId("doc-1");
        existingState.setIndexName("entities");
        existingState.setSearchTextHash("old-hash");

        when(indexingStateRepository.findByDocumentIdAndIndexName("doc-1", "entities"))
                .thenReturn(Optional.of(existingState));

        IndexDocumentRequest request = createRequest("doc-1", "unit", "Radar Alpha",
                "Updated description of the radar unit");

        float[] newEmbedding = {0.4f, 0.5f, 0.6f};
        when(embeddingProvider.generateEmbedding("Updated description of the radar unit"))
                .thenReturn(newEmbedding);

        indexingService.updateDocument("doc-1", request);

        verify(embeddingProvider).generateEmbedding("Updated description of the radar unit");

        ArgumentCaptor<SearchDocument> captor = ArgumentCaptor.forClass(SearchDocument.class);
        verify(openSearchAdapter).indexDocument(captor.capture());
        assertThat(captor.getValue().getEmbedding()).isEqualTo(newEmbedding);
    }

    @Test
    void updateDocument_shouldReuseEmbeddingWhenSemanticContentUnchanged() {
        String searchText = "Long range radar surveillance unit";
        // Compute actual hash
        java.security.MessageDigest digest;
        String hash;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(searchText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash = java.util.HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        IndexingState existingState = new IndexingState();
        existingState.setDocumentId("doc-1");
        existingState.setIndexName("entities");
        existingState.setSearchTextHash(hash);
        existingState.setStatus(IndexingStatus.INDEXED);
        existingState.setDocumentSource("{\"id\":\"doc-1\"}");

        when(indexingStateRepository.findByDocumentIdAndIndexName("doc-1", "entities"))
                .thenReturn(Optional.of(existingState));

        List<Float> existingEmbedding = List.of(0.1f, 0.2f, 0.3f);
        when(openSearchAdapter.getDocument("entities", "doc-1"))
                .thenReturn(Map.of("embedding", existingEmbedding));

        IndexDocumentRequest request = createRequest("doc-1", "unit", "Radar Alpha", searchText);

        indexingService.updateDocument("doc-1", request);

        verify(embeddingProvider, never()).generateEmbedding(anyString());
    }

    @Test
    void deleteDocument_shouldDeleteFromOpenSearchAndUpdateState() {
        IndexingState existingState = new IndexingState();
        existingState.setDocumentId("doc-1");
        existingState.setIndexName("entities");
        existingState.setStatus(IndexingStatus.INDEXED);

        when(indexingStateRepository.findByDocumentIdAndIndexName("doc-1", "entities"))
                .thenReturn(Optional.of(existingState));

        indexingService.deleteDocument("entities", "doc-1");

        verify(openSearchAdapter).deleteDocument("entities", "doc-1");

        ArgumentCaptor<IndexingState> captor = ArgumentCaptor.forClass(IndexingState.class);
        verify(indexingStateRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(IndexingStatus.DELETED);
        assertThat(captor.getValue().getDocumentSource()).isNull();
    }

    @Test
    void bulkIndex_shouldIndexAllDocuments() {
        List<IndexDocumentRequest> requests = List.of(
                createRequest("doc-1", "unit", "Alpha", "Search text 1"),
                createRequest("doc-2", "unit", "Beta", "Search text 2")
        );

        float[] embedding = {0.1f, 0.2f};
        when(embeddingProvider.generateEmbedding(anyString())).thenReturn(embedding);
        when(indexingStateRepository.findByDocumentIdAndIndexName(anyString(), anyString()))
                .thenReturn(Optional.empty());

        indexingService.bulkIndex(requests);

        ArgumentCaptor<List<SearchDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(openSearchAdapter).bulkIndex(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    // --- Helper ---

    private IndexDocumentRequest createRequest(String id, String type, String title, String searchText) {
        IndexDocumentRequest req = new IndexDocumentRequest();
        req.setId(id);
        req.setType(type);
        req.setTitle(title);
        req.setSearchText(searchText);
        return req;
    }
}
