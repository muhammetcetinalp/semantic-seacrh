package com.example.semantic_search.kafka;

import com.example.semantic_search.indexing.IndexDocumentRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/** Maps the example full-snapshot contract; JSON is never used as embedding text. */
public class JsonSearchEventMapper implements SearchEventMapper {

    private static final Set<String> RESERVED_FIELDS = Set.of(
            "id", "type", "title", "searchText", "shortText", "longText", "birim", "adres", "tarih", "konum",
            "tags", "metadata", "embedding", "createdAt", "updatedAt");
    private final ObjectMapper objectMapper;

    public JsonSearchEventMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    public SearchIndexingEvent read(String message) {
        if (message == null || message.isBlank()) {
            throw new InvalidSearchEventException("Event must be a JSON object; tombstones are not supported.");
        }
        try {
            SearchIndexingEvent event = objectMapper.readValue(message, SearchIndexingEvent.class);
            if (event == null || event.eventType() == null || event.eventType().isBlank()) {
                throw new InvalidSearchEventException("Event type is required.");
            }
            return event;
        } catch (JacksonException ex) {
            throw new InvalidSearchEventException("Event is not valid JSON for the configured contract.", ex);
        }
    }

    @Override
    public IndexDocumentRequest mapDocument(SearchIndexingEvent event, KafkaIndexingProperties.EventRoute route) {
        if (event.data() == null || !event.data().isObject()) {
            throw new InvalidSearchEventException("Upsert events require a full document snapshot in data.");
        }
        try {
            IndexDocumentRequest request = objectMapper.treeToValue(event.data(), IndexDocumentRequest.class);
            if (request.getStructuredFields() != null
                    && request.getStructuredFields().keySet().stream().anyMatch(RESERVED_FIELDS::contains)) {
                throw new InvalidSearchEventException("Structured fields cannot overwrite core document fields.");
            }

            // Extract nested incident fields if present in data.fields
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

            // Auto-derive searchText if not provided
            if (request.getSearchText() == null || request.getSearchText().isBlank()) {
                StringBuilder sb = new StringBuilder();
                if (request.getTitle() != null) sb.append(request.getTitle()).append("\n");
                if (request.getShortText() != null) sb.append(request.getShortText()).append("\n");
                if (request.getLongText() != null) sb.append(request.getLongText());
                request.setSearchText(sb.toString().trim());
            }

            // Identity and routing are controlled by the envelope and configuration.
            request.setId(event.documentId());
            request.setIndexName(route.indexName());
            request.setType(route.documentType());
            return request;
        } catch (JacksonException ex) {
            throw new InvalidSearchEventException("Document snapshot is invalid.", ex);
        }
    }
}
