package com.example.semantic_search.kafka;

import com.example.semantic_search.config.KafkaIndexingProperties;
import com.example.semantic_search.dto.IndexDocumentRequest;
import com.example.semantic_search.exception.InvalidSearchEventException;
import com.example.semantic_search.model.SearchIndexingEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
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
            tools.jackson.databind.JsonNode root = objectMapper.readTree(message);
            if (!root.isObject()) {
                throw new InvalidSearchEventException("Olay bir JSON nesnesi olmalıdır.");
            }

            // 1. Zarf (envelope) formatında gelen olaylar: {eventId, eventType, documentId, version, data}
            if (root.has("eventType") && (root.has("data") || root.has("documentId"))) {
                SearchIndexingEvent event = objectMapper.treeToValue(root, SearchIndexingEvent.class);
                if (event != null && event.eventType() != null && !event.eventType().isBlank()) {
                    return event;
                }
            }

            // 2. Ham / doğrudan doküman formatı (örn: {id: "...", title: "...", content: "..."})
            String docId = root.path("id").asText(
                    root.path("documentId").asText(
                            root.path("entityId").asText(java.util.UUID.randomUUID().toString())));
            String eventType = root.path("eventType").asText(
                    root.path("type").asText(
                            root.path("action").asText("UPSERT")));
            String eventId = root.path("eventId").asText(java.util.UUID.randomUUID().toString());
            long version = root.path("version").asLong(1L);

            return new SearchIndexingEvent(eventId, eventType, docId, version, root);
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

            // Gelen verideki tüm özel/ekstra alanları dinamik olarak structuredFields haritasına topla
            if (request.getStructuredFields() == null) {
                request.setStructuredFields(new java.util.HashMap<>());
            }
            if (event.data().isObject()) {
                try {
                    Map<String, Object> dataMap = objectMapper.convertValue(event.data(), Map.class);
                    if (dataMap != null) {
                        for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
                            String key = entry.getKey();
                            if (!RESERVED_FIELDS.contains(key) && !"fields".equals(key) && !"structuredFields".equals(key)) {
                                request.getStructuredFields().putIfAbsent(key, entry.getValue());
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Arama metni sağlanmadıysa başlık ve metinlerden otomatik türet
            if (request.getSearchText() == null || request.getSearchText().isBlank()) {
                StringBuilder sb = new StringBuilder();
                if (request.getTitle() != null && !request.getTitle().isBlank()) sb.append(request.getTitle()).append("\n");
                if (request.getShortText() != null && !request.getShortText().isBlank()) sb.append(request.getShortText()).append("\n");
                if (request.getLongText() != null && !request.getLongText().isBlank()) sb.append(request.getLongText()).append("\n");

                // data alanından content / icerik / text alanlarını da tara
                if (event.data() != null) {
                    String extraContent = event.data().path("content").asText(
                            event.data().path("text").asText(
                                    event.data().path("icerik").asText("")));
                    if (!extraContent.isBlank() && (request.getLongText() == null || !request.getLongText().contains(extraContent))) {
                        sb.append(extraContent);
                    }
                }

                String combined = sb.toString().trim();
                request.setSearchText(!combined.isBlank() ? combined : (request.getTitle() != null ? request.getTitle() : ""));
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
