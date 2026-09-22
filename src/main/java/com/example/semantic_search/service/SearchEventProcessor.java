package com.example.semantic_search.service;

import com.example.semantic_search.config.KafkaIndexingProperties;
import com.example.semantic_search.dto.IndexDocumentRequest;
import com.example.semantic_search.exception.InvalidSearchEventException;
import com.example.semantic_search.kafka.SearchEventMapper;
import com.example.semantic_search.model.IndexingState;
import com.example.semantic_search.model.SearchIndexingEvent;
import com.example.semantic_search.repository.IndexingStateRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka veya diğer mesaj kuyruklarından gelen arama olaylarını doğrulayan,
 * sürüm (versioning) ve bayatlık (staleness) kontrolü yapan, ardından
 * {@link IndexingService} üzerinden ekleme, güncelleme veya silme işlemlerini yürüten işlemci servisi.
 *
 * <p>İşlemler veritabanı seviyesinde {@code @Transactional} olarak çalışır ve
 * Kafka ofseti işlem onaylanmadan önce taahhüt edilmez (commit edilmez).</p>
 */
@Service
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
    @Transactional
    public Outcome process(String message) {
        SearchIndexingEvent event = mapper.read(message);
        KafkaIndexingProperties.EventRoute route = properties.getRoutes().get(event.eventType());
        if (route == null) return Outcome.IGNORED;
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

        IndexingState state = repository.findByDocumentIdAndIndexNameForUpdate(event.documentId(), route.indexName())
                .orElseGet(() -> {
                    IndexingState pending = new IndexingState();
                    pending.setDocumentId(event.documentId());
                    pending.setIndexName(route.indexName());
                    return repository.saveAndFlush(pending);
                });

        if (state.getLastEventVersion() != null && event.version() <= state.getLastEventVersion()) {
            return Outcome.STALE;
        }

        switch (route.operation()) {
            case UPSERT -> indexingService.updateDocument(event.documentId(), document);
            case DELETE -> indexingService.deleteDocument(route.indexName(), event.documentId());
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
