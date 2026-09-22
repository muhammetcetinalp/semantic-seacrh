package com.example.semantic_search.kafka;

import com.example.semantic_search.config.KafkaIndexingProperties;
import com.example.semantic_search.dto.IndexDocumentRequest;
import com.example.semantic_search.model.SearchIndexingEvent;

/**
 * Kafka üzerinden gelen ham arama ve indeksleme olay mesajlarını ayrıştıran
 * ve hedef indeksleme modeline ({@link IndexDocumentRequest}) dönüştüren eşleyici arayüzü.
 *
 * <p>Farklı domain olay şemaları veya harici kaynak API çağrıları gerektiğinde
 * bu arayüzün yeni bir implementasyonu sisteme enjekte edilebilir.</p>
 */
public interface SearchEventMapper {

    /**
     * Ham JSON mesaj dizgisini doğrulanabilir {@link SearchIndexingEvent} nesnesine dönüştürür.
     *
     * @param message Kafka'dan tüketilen ham mesaj metni
     * @return Ayrıştırılmış indeksleme olay nesnesi
     */
    SearchIndexingEvent read(String message);

    /**
     * İndeksleme olayını ve rota yapılandırmasını kullanarak OpenSearch indeksleme isteği oluşturur.
     *
     * @param event Tüketilen olay nesnesi
     * @param route İlgili olay tipi için tanımlanmış rota yapılandırması
     * @return İndekslenecek doküman isteği DTO'su
     */
    IndexDocumentRequest mapDocument(SearchIndexingEvent event, KafkaIndexingProperties.EventRoute route);
}
