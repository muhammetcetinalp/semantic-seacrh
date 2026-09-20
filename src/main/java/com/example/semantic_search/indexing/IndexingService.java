package com.example.semantic_search.indexing;

import com.example.semantic_search.configuration.SearchProperties;
import com.example.semantic_search.embedding.EmbeddingProvider;
import com.example.semantic_search.exception.DocumentNotFoundException;
import com.example.semantic_search.opensearch.OpenSearchAdapter;
import com.example.semantic_search.opensearch.OpenSearchDocumentSourceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final OpenSearchAdapter openSearchAdapter;
    private final EmbeddingProvider embeddingProvider;
    private final IndexingStateRepository indexingStateRepository;
    private final SearchProperties searchProperties;
    private final OpenSearchDocumentSourceMapper documentSourceMapper;

    private final Optional<com.example.semantic_search.qdrant.ColbertIndexingSyncService> colbertSyncService;

    @Autowired
    public IndexingService(OpenSearchAdapter openSearchAdapter,
                           EmbeddingProvider embeddingProvider,
                           IndexingStateRepository indexingStateRepository,
                           SearchProperties searchProperties,
                           OpenSearchDocumentSourceMapper documentSourceMapper,
                           Optional<com.example.semantic_search.qdrant.ColbertIndexingSyncService> colbertSyncService) {
        this.openSearchAdapter = openSearchAdapter;
        this.embeddingProvider = embeddingProvider;
        this.indexingStateRepository = indexingStateRepository;
        this.searchProperties = searchProperties;
        this.documentSourceMapper = documentSourceMapper;
        this.colbertSyncService = colbertSyncService != null ? colbertSyncService : Optional.empty();
    }

    public IndexingService(OpenSearchAdapter openSearchAdapter,
                           EmbeddingProvider embeddingProvider,
                           IndexingStateRepository indexingStateRepository,
                           SearchProperties searchProperties,
                           OpenSearchDocumentSourceMapper documentSourceMapper) {
        this(openSearchAdapter, embeddingProvider, indexingStateRepository, searchProperties, documentSourceMapper, Optional.empty());
    }

    @Transactional
    public void indexDocument(IndexDocumentRequest request) {
        String indexName = resolveIndexName(request.getIndexName());
        Instant now = Instant.now();

        openSearchAdapter.createIndexIfNotExists(indexName);

        float[] embedding = generateEmbeddingIfNeeded(request.getSearchText());

        SearchDocument document = SearchDocument.builder()
                .id(request.getId())
                .indexName(indexName)
                .type(request.getType())
                .title(request.getTitle())
                .searchText(request.getSearchText())
                .shortText(request.getShortText())
                .longText(request.getLongText())
                .birim(request.getBirim())
                .adres(request.getAdres())
                .tarih(request.getTarih())
                .konum(request.getKonum())
                .tags(request.getTags())
                .structuredFields(request.getStructuredFields())
                .metadata(request.getMetadata())
                .embedding(embedding)
                .createdAt(now)
                .updatedAt(now)
                .build();

        openSearchAdapter.indexDocument(document);
        saveIndexingState(request.getId(), indexName, request.getSearchText(),
                IndexingStatus.INDEXED, documentSourceMapper.toJson(document));

        colbertSyncService.ifPresent(sync -> {
            try {
                Map<String, Object> payload = new java.util.HashMap<>();
                if (request.getTitle() != null) payload.put("title", request.getTitle());
                if (request.getSearchText() != null) payload.put("searchText", request.getSearchText());
                if (request.getType() != null) payload.put("type", request.getType());
                if (request.getBirim() != null) payload.put("birim", request.getBirim());
                if (request.getTarih() != null) payload.put("tarih", request.getTarih());
                if (request.getAdres() != null) payload.put("adres", request.getAdres());
                if (request.getKonum() != null) payload.put("konum", request.getKonum());
                if (request.getShortText() != null) payload.put("shortText", request.getShortText());
                if (request.getLongText() != null) payload.put("longText", request.getLongText());
                sync.indexSingleDocument(request.getId(), request.getTitle(), request.getSearchText(), payload);
            } catch (Exception e) {
                log.warn("ColBERT incremental indexing skipped for {}: {}", request.getId(), e.getMessage());
            }
        });

        log.info("Indexed document: {} in {}", request.getId(), indexName);
    }

    @Transactional
    public void updateDocument(String documentId, IndexDocumentRequest request) {
        String indexName = resolveIndexName(request.getIndexName());
        Instant now = Instant.now();

        openSearchAdapter.createIndexIfNotExists(indexName);

        String newSearchTextHash = hash(request.getSearchText());
        Optional<IndexingState> existingState = indexingStateRepository
                .findByDocumentIdAndIndexName(documentId, indexName);

        boolean semanticContentChanged = existingState
                .map(state -> !Objects.equals(newSearchTextHash, state.getSearchTextHash())
                        || state.getStatus() != IndexingStatus.INDEXED)
                .orElse(true);

        float[] embedding;
        if (semanticContentChanged) {
            embedding = generateEmbeddingIfNeeded(request.getSearchText());
            log.debug("Semantic content changed for {} — re-generated embedding", documentId);
        } else {
            embedding = getExistingEmbedding(indexName, documentId, request.getSearchText());
            log.debug("Semantic content unchanged for {} — reusing existing embedding", documentId);
        }

        SearchDocument document = SearchDocument.builder()
                .id(documentId)
                .indexName(indexName)
                .type(request.getType())
                .title(request.getTitle())
                .searchText(request.getSearchText())
                .shortText(request.getShortText())
                .longText(request.getLongText())
                .birim(request.getBirim())
                .adres(request.getAdres())
                .tarih(request.getTarih())
                .konum(request.getKonum())
                .tags(request.getTags())
                .structuredFields(request.getStructuredFields())
                .metadata(request.getMetadata())
                .embedding(embedding)
                .createdAt(existingState.map(s -> s.getCreatedAt()).orElse(now))
                .updatedAt(now)
                .build();

        openSearchAdapter.indexDocument(document);
        saveIndexingState(documentId, indexName, request.getSearchText(),
                IndexingStatus.INDEXED, documentSourceMapper.toJson(document));

        colbertSyncService.ifPresent(sync -> {
            try {
                Map<String, Object> payload = new java.util.HashMap<>();
                if (request.getTitle() != null) payload.put("title", request.getTitle());
                if (request.getSearchText() != null) payload.put("searchText", request.getSearchText());
                if (request.getType() != null) payload.put("type", request.getType());
                if (request.getBirim() != null) payload.put("birim", request.getBirim());
                if (request.getTarih() != null) payload.put("tarih", request.getTarih());
                if (request.getAdres() != null) payload.put("adres", request.getAdres());
                if (request.getKonum() != null) payload.put("konum", request.getKonum());
                if (request.getShortText() != null) payload.put("shortText", request.getShortText());
                if (request.getLongText() != null) payload.put("longText", request.getLongText());
                sync.indexSingleDocument(documentId, request.getTitle(), request.getSearchText(), payload);
            } catch (Exception e) {
                log.warn("ColBERT sync for updated document {} skipped: {}", documentId, e.getMessage());
            }
        });

        log.info("Updated document: {} in {}", documentId, indexName);
    }

    @Transactional
    public void deleteDocument(String indexName, String documentId) {
        String resolvedIndex = resolveIndexName(indexName);

        openSearchAdapter.deleteDocument(resolvedIndex, documentId);
        colbertSyncService.ifPresent(sync -> sync.deleteSingleDocument(documentId));

        indexingStateRepository.findByDocumentIdAndIndexName(documentId, resolvedIndex)
                .ifPresent(state -> {
                    state.setStatus(IndexingStatus.DELETED);
                    state.setDocumentSource(null);
                    indexingStateRepository.save(state);
                });

        log.info("Deleted document: {} from {}", documentId, resolvedIndex);
    }

    @Transactional
    public void bulkIndex(List<IndexDocumentRequest> requests) {
        if (requests == null || requests.isEmpty()) return;

        String indexName = resolveIndexName(null);
        openSearchAdapter.createIndexIfNotExists(indexName);
        Instant now = Instant.now();

        List<SearchDocument> documents = requests.stream()
                .map(req -> {
                    String idx = resolveIndexName(req.getIndexName());
                    float[] embedding = generateEmbeddingIfNeeded(req.getSearchText());
                    return SearchDocument.builder()
                            .id(req.getId())
                            .indexName(idx)
                            .type(req.getType())
                            .title(req.getTitle())
                            .searchText(req.getSearchText())
                            .shortText(req.getShortText())
                            .longText(req.getLongText())
                            .birim(req.getBirim())
                            .adres(req.getAdres())
                            .tarih(req.getTarih())
                            .konum(req.getKonum())
                            .tags(req.getTags())
                            .structuredFields(req.getStructuredFields())
                            .metadata(req.getMetadata())
                            .embedding(embedding)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                })
                .toList();

        openSearchAdapter.bulkIndex(documents);

        for (int i = 0; i < requests.size(); i++) {
            IndexDocumentRequest req = requests.get(i);
            SearchDocument document = documents.get(i);
            saveIndexingState(req.getId(), resolveIndexName(req.getIndexName()),
                    req.getSearchText(), IndexingStatus.INDEXED, documentSourceMapper.toJson(document));
        }

        colbertSyncService.ifPresent(sync -> {
            try {
                for (IndexDocumentRequest req : requests) {
                    Map<String, Object> payload = new HashMap<>();
                    if (req.getTitle() != null) payload.put("title", req.getTitle());
                    if (req.getSearchText() != null) payload.put("searchText", req.getSearchText());
                    if (req.getType() != null) payload.put("type", req.getType());
                    if (req.getBirim() != null) payload.put("birim", req.getBirim());
                    if (req.getTarih() != null) payload.put("tarih", req.getTarih());
                    if (req.getAdres() != null) payload.put("adres", req.getAdres());
                    if (req.getKonum() != null) payload.put("konum", req.getKonum());
                    if (req.getShortText() != null) payload.put("shortText", req.getShortText());
                    if (req.getLongText() != null) payload.put("longText", req.getLongText());
                    sync.indexSingleDocument(req.getId(), req.getTitle(), req.getSearchText(), payload);
                }
            } catch (Exception e) {
                log.warn("ColBERT sync during bulk index skipped: {}", e.getMessage());
            }
        });

        log.info("Bulk indexed {} documents", requests.size());
    }

    // ---- Private helpers ----

    private float[] generateEmbeddingIfNeeded(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return null;
        }
        return embeddingProvider.generateEmbedding(searchText);
    }

    @SuppressWarnings("unchecked")
    private float[] getExistingEmbedding(String indexName, String documentId, String searchText) {
        try {
            Map<String, Object> doc = openSearchAdapter.getDocument(indexName, documentId);
            Object embedding = doc.get("embedding");
            if (embedding instanceof List<?> list) {
                float[] result = new float[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    result[i] = ((Number) list.get(i)).floatValue();
                }
                return result;
            }
            return generateEmbeddingIfNeeded(searchText);
        } catch (DocumentNotFoundException e) {
            return generateEmbeddingIfNeeded(searchText);
        }
    }

    private void saveIndexingState(String documentId, String indexName,
                                    String searchText, IndexingStatus status, String documentSource) {
        IndexingState state = indexingStateRepository
                .findByDocumentIdAndIndexName(documentId, indexName)
                .orElseGet(() -> {
                    IndexingState s = new IndexingState();
                    s.setDocumentId(documentId);
                    s.setIndexName(indexName);
                    return s;
                });

        state.setStatus(status);
        state.setSearchTextHash(hash(searchText));
        state.setLastIndexedAt(Instant.now());
        state.setErrorMessage(null);
        state.setDocumentSource(documentSource);
        indexingStateRepository.save(state);
    }

    private String resolveIndexName(String indexName) {
        return (indexName != null && !indexName.isBlank())
                ? indexName
                : searchProperties.getDefaultIndexName();
    }

    private String hash(String text) {
        if (text == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
