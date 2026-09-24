package com.example.semantic_search.service;

import com.example.semantic_search.config.KafkaIndexingProperties;
import com.example.semantic_search.dto.IndexDocumentRequest;
import com.example.semantic_search.exception.InvalidSearchEventException;
import com.example.semantic_search.kafka.SearchEventMapper;
import com.example.semantic_search.model.IndexingState;
import com.example.semantic_search.model.SearchIndexingEvent;
import com.example.semantic_search.repository.IndexingStateRepository;
import jakarta.validation.Validator;

/**
 * Kafka veya diğer mesaj kuyruklarından gelen arama olaylarını doğrulayan,
 * sürüm (versioning) ve bayatlık (staleness) kontrolü yapan, ardından
 * {@link IndexingService} üzerinden ekleme, güncelleme veya silme işlemlerini yürüten işlemci servisi.
 */
public class SearchEventProcessor {

    /**
     * Olay işleme sonucunu belirten durum enum'ı.
     */
    public enum Outcome {
        /** Olay başarıyla işlendi ve indekslendi. */
        PROCESSED,
        /** Olay tipi için rota tanımlı olmadığından yoksayıldı. */
        IGNORED,
        /** Olay sürümü mevcut indeksteki sürümden eski veya eşit olduğundan atlandı. */
        STALE
    }

    private final SearchEventMapper mapper;
    private final KafkaIndexingProperties properties;
    private final IndexingService indexingService;
    private final IndexingStateRepository repository;
    private final Validator validator;

    /**
     * SearchEventProcessor bağımlılıklarını enjekte eden yapıcı metot.
     *
     * @param mapper Olay ayrıştırıcı ve dönüştürücü
     * @param properties Kafka yapılandırma özellikleri
     * @param indexingService İndeksleme iş mantığı servisi
     * @param repository Durum deposu
     * @param validator Bean doğrulayıcısı
     */
    public SearchEventProcessor(SearchEventMapper mapper, KafkaIndexingProperties properties,
                                IndexingService indexingService, IndexingStateRepository repository,
                                Validator validator) {
        this.mapper = mapper;
        this.properties = properties;
        this.indexingService = indexingService;
        this.repository = repository;
        this.validator = validator;
    }

    /**
     * Ham Kafka mesajını ayrıştırır, doğrular, sürüm kontrolünü yapar ve indeksleme operasyonunu çalıştırır.
     *
     * @param message Ham JSON mesajı
     * @return İşlem sonucu (PROCESSED, IGNORED veya STALE)
     * @throws InvalidSearchEventException Olay formatı geçersiz ise
     */
    public Outcome process(String message) {
        SearchIndexingEvent event = mapper.read(message);
        KafkaIndexingProperties.EventRoute configuredRoute = properties.getRoutes().get(event.eventType());
        final KafkaIndexingProperties.EventRoute route;
        if (configuredRoute != null) {
            route = configuredRoute;
        } else {
            String fallbackIndex = properties.getRoutes().values().stream()
                    .findFirst()
                    .map(KafkaIndexingProperties.EventRoute::indexName)
                    .orElse("olaylar");
            KafkaIndexingProperties.Operation op = (event.eventType() != null && event.eventType().toUpperCase().contains("DELETE"))
                    ? KafkaIndexingProperties.Operation.DELETE
                    : KafkaIndexingProperties.Operation.UPSERT;
            route = new KafkaIndexingProperties.EventRoute(op, fallbackIndex, event.eventType() != null ? event.eventType() : "DOCUMENT");
        }
        validate(event);

        // Eşleme ve doğrulama harici işlem yapılmadan önce gerçekleşir
        IndexDocumentRequest document = null;
        if (route.operation() == KafkaIndexingProperties.Operation.UPSERT) {
            document = mapper.mapDocument(event, route);
            validate(document);
            if (!event.documentId().equals(document.getId()) || !route.indexName().equals(document.getIndexName())) {
                throw new InvalidSearchEventException("Eşlenen doküman kimliği veya indeksi olay rotasıyla uyuşmuyor.");
            }
        }

        final String indexName = route.indexName();
        IndexingState state = repository.findByDocumentIdAndIndexNameForUpdate(event.documentId(), indexName)
                .orElseGet(() -> {
                    IndexingState pending = new IndexingState();
                    pending.setDocumentId(event.documentId());
                    pending.setIndexName(indexName);
                    return repository.saveAndFlush(pending);
                });

        if (state.getLastEventVersion() != null && event.version() <= state.getLastEventVersion()) {
            return Outcome.STALE;
        }

        switch (route.operation()) {
            case UPSERT -> indexingService.updateDocument(event.documentId(), document);
            case DELETE -> indexingService.deleteDocument(indexName, event.documentId());
        }

        state.setLastEventId(event.eventId());
        state.setLastEventVersion(event.version());
        repository.saveAndFlush(state);
        return Outcome.PROCESSED;
    }

    /**
     * Jakarta Validator kullanarak nesnenin kısıtlamalara uyup uymadığını kontrol eder.
     *
     * @param value Doğrulanacak nesne
     * @throws InvalidSearchEventException Doğrulama başarısız olursa
     */
    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) {
            throw new InvalidSearchEventException("Seçilen olay veya eşlenen doküman validasyondan geçemedi.");
        }
    }
}
