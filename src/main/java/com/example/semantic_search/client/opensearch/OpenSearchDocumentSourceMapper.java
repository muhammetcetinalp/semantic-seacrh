package com.example.semantic_search.client.opensearch;

import com.example.semantic_search.model.SearchDocument;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arama dokümanlarını ({@link SearchDocument}) OpenSearch ve PostgreSQL üzerinde saklanmaya
 * uygun JSON kaynak haritasına (document source map) dönüştüren eşleme (mapper) bileşeni.
 *
 * <p>Temel alanların (core fields) yanı sıra dinamik yapısal alanların (structured fields)
 * çakışma kontrollerini ve coğrafi konum (geo_point) ayrıştırmasını gerçekleştirir.</p>
 */
@Component
public class OpenSearchDocumentSourceMapper {

    private static final Set<String> CORE_FIELDS = Set.of(
            "id", "type", "title", "searchText", "metadata",
            "embedding", "createdAt", "updatedAt",
            "shortText", "longText", "birim", "adres", "tarih", "konum");

    private final ObjectMapper objectMapper;

    /**
     * JSON serileştirme işlemleri için ObjectMapper enjekte eden yapıcı metot.
     *
     * @param objectMapper Jackson nesne dönüştürücüsü
     */
    public OpenSearchDocumentSourceMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * {@link SearchDocument} nesnesini OpenSearch indeks dokümanı formatındaki anahtar-değer haritasına çevirir.
     *
     * @param document İndekslenecek doküman modeli
     * @return OpenSearch'e gönderilecek kaynak harita
     * @throws IllegalArgumentException Yapısal alanlar temel alanlarla çakışırsa fırlatılır
     */
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
        if (document.getMetadata() != null) source.put("metadata", document.getMetadata());
        if (document.getEmbedding() != null) source.put("embedding", toFloatList(document.getEmbedding()));
        if (document.getCreatedAt() != null) source.put("createdAt", document.getCreatedAt().toString());
        if (document.getUpdatedAt() != null) source.put("updatedAt", document.getUpdatedAt().toString());

        if (document.getStructuredFields() != null) {
            Set<String> collisions = new java.util.HashSet<>(document.getStructuredFields().keySet());
            collisions.retainAll(CORE_FIELDS);
            if (!collisions.isEmpty()) {
                throw new IllegalArgumentException("Yapısal alanlar temel alanlarla çakışıyor: " + collisions);
            }
            source.putAll(document.getStructuredFields());
        }
        return source;
    }

    /**
     * Konum verisini OpenSearch'ün beklediği geo_point formatına ({lat: x, lon: y} veya harita) dönüştürür.
     *
     * @param konum Konum objesi (String veya Map)
     * @return Ayrıştırılmış geo_point nesnesi
     */
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

    /**
     * Dokümanı JSON dizgisine serileştirir.
     *
     * @param document İndekslenecek doküman
     * @return JSON dizesi
     * @throws IllegalArgumentException JSON serileştirme başarısız olursa fırlatılır
     */
    public String toJson(SearchDocument document) {
        try {
            return objectMapper.writeValueAsString(toSource(document));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("OpenSearch dokümanı JSON olarak serileştirilemedi", exception);
        }
    }

    /**
     * Temel float dizisini Float listesine dönüştürür.
     *
     * @param values İlkel float dizisi
     * @return Float nesneleri listesi
     */
    private List<Float> toFloatList(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) result.add(value);
        return result;
    }
}
