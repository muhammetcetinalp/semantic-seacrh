package com.example.semantic_search.kafka;

import com.example.semantic_search.config.KafkaIndexingProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

class JsonSearchEventMapperTest {
    private final JsonSearchEventMapper mapper = new JsonSearchEventMapper(JsonMapper.builder().build());
    private final KafkaIndexingProperties.EventRoute route = new KafkaIndexingProperties.EventRoute(
            KafkaIndexingProperties.Operation.UPSERT, "entities", "entity");

    @Test
    void routesIdentityFromConfigurationAndMapsOnlySearchFields() {
        var event = mapper.read("""
                {"eventId":"e1","eventType":"ENTITY_CREATED","documentId":"doc1","version":1,
                 "extraEnvelopeField":"ignored","data":{"id":"wrong","indexName":"wrong","type":"wrong",
                 "title":"Radar","searchText":"radar surveillance","structuredFields":{"region":"ANKARA"}}}
                """);
        var document = mapper.mapDocument(event, route);
        assertThat(document.getId()).isEqualTo("doc1");
        assertThat(document.getIndexName()).isEqualTo("entities");
        assertThat(document.getType()).isEqualTo("entity");
        assertThat(document.getSearchText()).isEqualTo("radar surveillance");
        assertThat(document.getStructuredFields()).containsEntry("region", "ANKARA");
    }

    @Test
    void rejectsMalformedMessagesAndTombstones() {
        for (String message : new String[]{null, "", "not-json", "null", "{}", "[]"}) {
            assertThatThrownBy(() -> mapper.read(message)).isInstanceOf(InvalidSearchEventException.class);
        }
    }

    @Test
    void rejectsMissingSnapshotsAndReservedStructuredFields() {
        assertThatThrownBy(() -> mapper.mapDocument(mapper.read("{\"eventType\":\"ENTITY_CREATED\"}"), route))
                .isInstanceOf(InvalidSearchEventException.class);
        var event = mapper.read("""
                {"eventType":"ENTITY_CREATED","data":{"structuredFields":{"embedding":[1,2]}}}
                """);
        assertThatThrownBy(() -> mapper.mapDocument(event, route)).isInstanceOf(InvalidSearchEventException.class);
    }
}
