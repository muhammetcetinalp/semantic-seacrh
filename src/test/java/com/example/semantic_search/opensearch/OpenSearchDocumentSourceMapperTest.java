package com.example.semantic_search.opensearch;

import com.example.semantic_search.indexing.SearchDocument;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class OpenSearchDocumentSourceMapperTest {
    private final OpenSearchDocumentSourceMapper mapper =
            new OpenSearchDocumentSourceMapper(JsonMapper.builder().build());

    @Test
    @SuppressWarnings("unchecked")
    void createsTheSameFlatJsonShapeExpectedByOpenSearch() throws Exception {
        SearchDocument document = SearchDocument.builder()
                .id("radar-1")
                .indexName("entities")
                .type("entity")
                .title("Radar")
                .searchText("long range radar")
                .tags(List.of("radar"))
                .metadata(Map.of("source", "kafka"))
                .structuredFields(Map.of("region", "ANKARA", "priority", 5))
                .embedding(new float[]{0.1f, 0.2f})
                .createdAt(Instant.parse("2026-09-19T10:15:30Z"))
                .updatedAt(Instant.parse("2026-09-19T10:16:30Z"))
                .build();

        Map<String, Object> source = mapper.toSource(document);
        assertThat(source).containsEntry("id", "radar-1")
                .containsEntry("region", "ANKARA")
                .containsEntry("priority", 5)
                .doesNotContainKeys("indexName", "structuredFields");
        assertThat(source.get("embedding")).isEqualTo(List.of(0.1f, 0.2f));

        Map<String, Object> storedJson = JsonMapper.builder().build().readValue(mapper.toJson(document), Map.class);
        assertThat(storedJson).containsEntry("id", "radar-1")
                .containsEntry("region", "ANKARA")
                .containsEntry("priority", 5);
        assertThat((List<Number>) storedJson.get("embedding"))
                .extracting(Number::doubleValue)
                .containsExactly(0.1, 0.2);
    }

    @Test
    void rejectsStructuredFieldsThatCouldOverwriteCoreFields() {
        SearchDocument document = SearchDocument.builder()
                .id("radar-1").type("entity")
                .structuredFields(Map.of("embedding", List.of(9, 9)))
                .build();
        assertThatThrownBy(() -> mapper.toSource(document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embedding");
    }
}
