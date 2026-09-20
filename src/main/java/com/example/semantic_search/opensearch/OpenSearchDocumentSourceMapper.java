package com.example.semantic_search.opensearch;

import com.example.semantic_search.indexing.SearchDocument;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the single canonical document source used by Oracle and OpenSearch. */
@Component
public class OpenSearchDocumentSourceMapper {

    private static final Set<String> CORE_FIELDS = Set.of(
            "id", "type", "title", "searchText", "tags", "metadata",
            "embedding", "createdAt", "updatedAt",
            "shortText", "longText", "birim", "adres", "tarih", "konum");

    private final ObjectMapper objectMapper;

    public OpenSearchDocumentSourceMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> toSource(SearchDocument document) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", document.getId());
        source.put("type", document.getType());
        if (document.getTitle() != null) source.put("title", document.getTitle());
        if (document.getSearchText() != null) source.put("searchText", document.getSearchText());
        if (document.getShortText() != null) source.put("shortText", document.getShortText());
        if (document.getLongText() != null) source.put("longText", document.getLongText());
        if (document.getBirim() != null) source.put("birim", document.getBirim());
        if (document.getAdres() != null) source.put("adres", document.getAdres());
        if (document.getTarih() != null) source.put("tarih", document.getTarih());
        if (document.getKonum() != null) source.put("konum", parseGeoPoint(document.getKonum()));
        if (document.getTags() != null) source.put("tags", document.getTags());
        if (document.getMetadata() != null) source.put("metadata", document.getMetadata());
        if (document.getEmbedding() != null) source.put("embedding", toFloatList(document.getEmbedding()));
        if (document.getCreatedAt() != null) source.put("createdAt", document.getCreatedAt().toString());
        if (document.getUpdatedAt() != null) source.put("updatedAt", document.getUpdatedAt().toString());

        if (document.getStructuredFields() != null) {
            Set<String> collisions = new java.util.HashSet<>(document.getStructuredFields().keySet());
            collisions.retainAll(CORE_FIELDS);
            if (!collisions.isEmpty()) {
                throw new IllegalArgumentException("Structured fields conflict with core fields: " + collisions);
            }
            source.putAll(document.getStructuredFields());
        }
        return source;
    }

    private Object parseGeoPoint(Object konum) {
        if (konum instanceof Map) return konum;
        if (konum instanceof String s && s.contains(",")) {
            try {
                String[] parts = s.split(",");
                if (parts.length == 2) {
                    double lat = Double.parseDouble(parts[0].trim());
                    double lon = Double.parseDouble(parts[1].trim());
                    return Map.of("lat", lat, "lon", lon);
                }
            } catch (Exception ignored) {
            }
        }
        return konum;
    }

    public String toJson(SearchDocument document) {
        try {
            return objectMapper.writeValueAsString(toSource(document));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("OpenSearch document cannot be serialized as JSON", exception);
        }
    }

    private List<Float> toFloatList(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) result.add(value);
        return result;
    }
}
