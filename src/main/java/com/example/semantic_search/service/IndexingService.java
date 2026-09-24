package com.example.semantic_search.service;

import com.example.semantic_search.client.embedding.EmbeddingProvider;
import com.example.semantic_search.client.opensearch.OpenSearchAdapter;
import com.example.semantic_search.client.opensearch.OpenSearchDocumentSourceMapper;
import com.example.semantic_search.config.SearchProperties;
import com.example.semantic_search.dto.IndexDocumentRequest;
import com.example.semantic_search.exception.DocumentNotFoundException;
import com.example.semantic_search.model.IndexingState;
import com.example.semantic_search.model.IndexingStatus;
import com.example.semantic_search.model.SearchDocument;
import com.example.semantic_search.repository.IndexingStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * Doküman indeksleme, güncelleme, silme ve durum takibi (PostgreSQL IndexingState)
 * süreçlerini yöneten temel iş mantığı servisi.
 *
 * <p>Sorumlulukları:
 * <ul>
 *   <li>Tekil ve toplu doküman indeksleme.</li>
 *   <li>Metin değişmediğinde pahalı embedding üretiminden kaçınmak için SHA-256 hash tabanlı önbellek/değişiklik kontrolü.</li>
 *   <li>İndeksleme durumunu PostgreSQL üzerinde transactional olarak kaydetme.</li>
 * </ul>
 * </p>
 */
@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final OpenSearchAdapter openSearchAdapter;
    private final EmbeddingProvider embeddingProvider;
    private final IndexingStateRepository indexingStateRepository;
    private final SearchProperties searchProperties;
    private final OpenSearchDocumentSourceMapper documentSourceMapper;

    /**
     * IndexingService için tüm bağımlılıkları enjekte eden yapıcı metot.
     *
     * @param openSearchAdapter OpenSearch istemci adaptörü
     * @param embeddingProvider Vektör sağlayıcı
     * @param indexingStateRepository İndeksleme durum tablosu JPA deposu
     * @param searchProperties Genel arama ayarları
     * @param documentSourceMapper Doküman kaynak dönüştürücüsü
     */
    @Autowired
    public IndexingService(OpenSearchAdapter openSearchAdapter,
                           EmbeddingProvider embeddingProvider,
                           IndexingStateRepository indexingStateRepository,
                           SearchProperties searchProperties,
                           OpenSearchDocumentSourceMapper documentSourceMapper) {
        this.openSearchAdapter = openSearchAdapter;
        this.embeddingProvider = embeddingProvider;
        this.indexingStateRepository = indexingStateRepository;
        this.searchProperties = searchProperties;
        this.documentSourceMapper = documentSourceMapper;
    }

    /**
     * Yeni bir dokümanı OpenSearch ve PostgreSQL üzerinde indeksler.
     *
     * @param request İndekslenecek doküman isteği DTO'su
     */
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
                .structuredFields(request.getStructuredFields())
                .metadata(request.getMetadata())
                .embedding(embedding)
                .createdAt(now)
                .updatedAt(now)
                .build();

        openSearchAdapter.indexDocument(document);
        saveIndexingState(request.getId(), indexName, request.getSearchText(),
                IndexingStatus.INDEXED, documentSourceMapper.toJson(document));

        log.info("Doküman indekslendi: {} ({})", request.getId(), indexName);
    }

    /**
     * Mevcut bir dokümanı günceller. Metin içeriği değişmemişse mevcut vektörü yeniden kullanarak performansı korur.
     *
     * @param documentId Güncellenecek doküman kimliği
     * @param request Yeni doküman verisi
     */
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
            log.debug("Doküman ({}) metin içeriği değişti — yeni embedding üretildi", documentId);
        } else {
            embedding = getExistingEmbedding(indexName, documentId, request.getSearchText());
            log.debug("Doküman ({}) metin içeriği değişmedi — mevcut embedding korundu", documentId);
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
                .structuredFields(request.getStructuredFields())
                .metadata(request.getMetadata())
                .embedding(embedding)
                .createdAt(existingState.map(IndexingState::getCreatedAt).orElse(now))
                .updatedAt(now)
                .build();

        openSearchAdapter.indexDocument(document);
        saveIndexingState(documentId, indexName, request.getSearchText(),
                IndexingStatus.INDEXED, documentSourceMapper.toJson(document));

        log.info("Doküman güncellendi: {} ({})", documentId, indexName);
    }

    /**
     * Dokümanı OpenSearch'ten siler; PostgreSQL durumunu DELETED olarak günceller.
     *
     * @param indexName Hedef indeks adı
     * @param documentId Silinecek doküman ID'si
     */
    public void deleteDocument(String indexName, String documentId) {
        String resolvedIndex = resolveIndexName(indexName);

        openSearchAdapter.deleteDocument(resolvedIndex, documentId);

        indexingStateRepository.findByDocumentIdAndIndexName(documentId, resolvedIndex)
                .ifPresent(state -> {
                    state.setStatus(IndexingStatus.DELETED);
                    state.setDocumentSource(null);
                    indexingStateRepository.save(state);
                });

        log.info("Doküman silindi: {} ({})", documentId, resolvedIndex);
    }

    /**
     * Çoklu doküman listesini OpenSearch Bulk API ile toplu indeksler.
     *
     * @param requests Doküman istekleri listesi
     */
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

        log.info("{} adet doküman toplu olarak indekslendi", requests.size());
    }

    /**
     * Metin boş değilse vektör gömme üretir.
     *
     * @param searchText Arama metni
     * @return Float vektör dizisi veya null
     */
    private float[] generateEmbeddingIfNeeded(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return null;
        }
        return embeddingProvider.generateEmbedding(searchText);
    }

    /**
     * Dokümanın OpenSearch'teki mevcut gömme vektörünü çeker veya yeniden üretir.
     *
     * @param indexName İndeks adı
     * @param documentId Doküman ID'si
     * @param searchText Arama metni
     * @return Float vektör dizisi
     */
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

    /**
     * İndeksleme durumunu PostgreSQL IndexingState tablosuna yazar veya günceller.
     *
     * @param documentId Doküman ID'si
     * @param indexName İndeks adı
     * @param searchText Arama metni
     * @param status İndeksleme durumu
     * @param documentSource Kaynak doküman JSON metni
     */
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

    /**
     * Belirtilen indeks adını çözer veya varsayılanı döner.
     *
     * @param indexName İndeks adı
     * @return İndeks adı
     */
    public String resolveIndexName(String indexName) {
        return (indexName != null && !indexName.isBlank())
                ? indexName
                : searchProperties.getDefaultIndexName();
    }

    /**
     * Belirtilen indeksteki toplam doküman sayısını döner.
     *
     * @param indexName İndeks adı
     * @return Doküman sayısı
     */
    public long countDocuments(String indexName) {
        return openSearchAdapter.countDocuments(resolveIndexName(indexName));
    }

    /**
     * Verilen metnin SHA-256 özetini (hex formatında) hesaplar.
     *
     * @param text Özetlenecek metin
     * @return Hex dizgisi
     */
    private String hash(String text) {
        if (text == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algoritması bulunamadı", e);
        }
    }
}
