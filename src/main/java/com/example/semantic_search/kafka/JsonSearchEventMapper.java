package com.example.semantic_search.kafka;

import com.example.semantic_search.config.KafkaIndexingProperties;
import com.example.semantic_search.dto.IndexDocumentRequest;
import com.example.semantic_search.exception.InvalidSearchEventException;
import com.example.semantic_search.model.SearchIndexingEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * Kafka üzerinden iletilen tam anlık görüntü (full-snapshot) JSON olaylarını ayrıştıran
 * ve standart {@link IndexDocumentRequest} nesnesine dönüştüren eşleyici bileşeni.
 *
 * <p>Güvenlik amacıyla JSON ham içeriğinin doğrudan gömme (embedding) metni olarak
 * kullanılmasını engeller; başlık, kısa metin ve uzun metin üzerinden temiz arama metni türetir.</p>
 */
public class JsonSearchEventMapper implements SearchEventMapper {

    private static final Set<String> RESERVED_FIELDS = Set.of(
            "id", "type", "title", "searchText", "shortText", "longText", "birim", "adres", "tarih", "konum",
            "metadata", "embedding", "createdAt", "updatedAt");

    private final ObjectMapper objectMapper;

    /**
     * JSON serileştirme işlemleri için ObjectMapper enjekte eden yapıcı metot.
     *
     * @param objectMapper Jackson JSON dönüştürücüsü
     */
    public JsonSearchEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Gelen JSON metnini {@link SearchIndexingEvent} modeline ayrıştırır ve temel kontrolleri yapar.
     *
     * @param message Ham Kafka iletisi
     * @return Ayrıştırılmış olay modeli
     * @throws InvalidSearchEventException Mesaj geçersiz JSON ise veya olay tipi boşsa fırlatılır
     */
    @Override
    public SearchIndexingEvent read(String message) {
        if (message == null || message.isBlank()) {
            throw new InvalidSearchEventException("Olay bir JSON nesnesi olmalıdır; boş veya mezartaşı (tombstone) mesajlar desteklenmez.");
        }
        try {
            SearchIndexingEvent event = objectMapper.readValue(message, SearchIndexingEvent.class);
            if (event == null || event.eventType() == null || event.eventType().isBlank()) {
                throw new InvalidSearchEventException("Olay tipi (eventType) zorunludur.");
            }
            return event;
        } catch (JacksonException ex) {
            throw new InvalidSearchEventException("Olay sözleşmeye uygun geçerli bir JSON değil.", ex);
        }
    }

    /**
     * Olay verisi içerisindeki anlık doküman görüntüsünü IndexDocumentRequest nesnesine dönüştürür.
     *
     * @param event Olay nesnesi
     * @param route Rota tanımı
     * @return Hazırlanan doküman indeksleme isteği
     * @throws InvalidSearchEventException Doküman verisi eksik veya kural dışı ise fırlatılır
     */
    @Override
    public IndexDocumentRequest mapDocument(SearchIndexingEvent event, KafkaIndexingProperties.EventRoute route) {
        if (event.data() == null || !event.data().isObject()) {
            throw new InvalidSearchEventException("Ekleme/Güncelleme (UPSERT) olayları veri (data) alanında tam doküman anlık görüntüsü gerektirir.");
        }
        try {
            IndexDocumentRequest request = objectMapper.treeToValue(event.data(), IndexDocumentRequest.class);
            if (request.getStructuredFields() != null
                    && request.getStructuredFields().keySet().stream().anyMatch(RESERVED_FIELDS::contains)) {
                throw new InvalidSearchEventException("Yapısal dinamik alanlar temel çekirdek alanların üzerine yazamaz.");
            }

            // data.fields altında yuvalanmış olay alanları varsa ayıkla
            tools.jackson.databind.JsonNode fieldsNode = event.data().get("fields");
            if (fieldsNode != null && fieldsNode.isObject()) {
                if (request.getBirim() == null && fieldsNode.has("birim")) {
                    request.setBirim(fieldsNode.get("birim").asText());
                }
                if (request.getAdres() == null && fieldsNode.has("adres")) {
                    request.setAdres(fieldsNode.get("adres").asText());
                }
                if (request.getTarih() == null && fieldsNode.has("tarih")) {
                    request.setTarih(fieldsNode.get("tarih").asText());
                }
                if (request.getKonum() == null && fieldsNode.has("konum")) {
                    request.setKonum(fieldsNode.get("konum").asText());
                }
            }

            // Arama metni sağlanmadıysa başlık ve metinlerden otomatik türet
            if (request.getSearchText() == null || request.getSearchText().isBlank()) {
                StringBuilder sb = new StringBuilder();
                if (request.getTitle() != null) sb.append(request.getTitle()).append("\n");
                if (request.getShortText() != null) sb.append(request.getShortText()).append("\n");
                if (request.getLongText() != null) sb.append(request.getLongText());
                request.setSearchText(sb.toString().trim());
            }

            // Doküman kimliği ve hedef rota zarf (envelope) üzerinden atanır
            request.setId(event.documentId());
            request.setIndexName(route.indexName());
            request.setType(route.documentType());
            return request;
        } catch (JacksonException ex) {
            throw new InvalidSearchEventException("Doküman anlık görüntüsü geçerli değil.", ex);
        }
    }
}
