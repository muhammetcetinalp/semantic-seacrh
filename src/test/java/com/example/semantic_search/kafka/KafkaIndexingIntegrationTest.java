package com.example.semantic_search.kafka;

import com.example.semantic_search.embedding.EmbeddingProvider;
import com.example.semantic_search.indexing.*;
import com.example.semantic_search.opensearch.OpenSearchAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Kafka delivery and JPA transactions, without an installed broker or Docker. */
@SpringBootTest
@ActiveProfiles("kafka-test")
@EmbeddedKafka(partitions = 1, topics = {"indexing-test", "indexing-test-dlt"})
@DirtiesContext
class KafkaIndexingIntegrationTest {
    @MockitoBean private OpenSearchAdapter adapter;
    @MockitoBean private EmbeddingProvider embeddings;
    @Autowired private KafkaTemplate<Object, Object> template;
    @Autowired private EmbeddedKafkaBroker broker;
    @Autowired private IndexingStateRepository repository;
    @Autowired private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        when(embeddings.generateEmbedding(anyString())).thenReturn(new float[]{0.1f, 0.2f});
    }

    @Test
    void processesSnapshotsDeletesAndIgnoresDuplicatesOlderVersionsAndUnselectedTypes() throws Exception {
        String id = UUID.randomUUID().toString();
        send(event(id, "ENTITY_CREATED", 1, "radar"));
        IndexingState indexedState = waitForVersion(id, 1);
        assertThat(indexedState.getStatus()).isEqualTo(IndexingStatus.INDEXED);
        assertThat(indexedState.getDocumentSource())
                .contains("\"id\":\"" + id + "\"")
                .contains("\"searchText\":\"radar\"")
                .contains("\"embedding\":[0.1,0.2]");
        verify(adapter).indexDocument(argThat(doc -> doc.getId().equals(id)
                && doc.getType().equals("entity") && doc.getSearchText().equals("radar")));

        double staleBefore = count("stale");
        send(event(id, "ENTITY_CREATED", 1, "radar"));
        send(event(id, "ENTITY_UPDATED", 0, "old radar"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("stale")).isEqualTo(staleBefore + 2));
        verify(adapter, times(1)).indexDocument(any());

        double ignoredBefore = count("ignored");
        send("{\"eventType\":\"UNSELECTED\"}");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("ignored")).isEqualTo(ignoredBefore + 1));

        when(adapter.getDocument("kafka-entities", id)).thenReturn(Map.of("embedding", List.of(0.1f, 0.2f)));
        send(event(id, "ENTITY_UPDATED", 2, "radar"));
        waitForVersion(id, 2);
        verify(embeddings, times(1)).generateEmbedding(anyString());
        send(event(id, "ENTITY_UPDATED", 3, "new radar"));
        waitForVersion(id, 3);
        verify(embeddings, times(2)).generateEmbedding(anyString());

        send(event(id, "ENTITY_DELETED", 4, null));
        IndexingState deletedState = waitForVersion(id, 4);
        assertThat(deletedState.getStatus()).isEqualTo(IndexingStatus.DELETED);
        assertThat(deletedState.getDocumentSource()).isNull();
        verify(adapter).deleteDocument("kafka-entities", id);
        send(event(id, "ENTITY_CREATED", 3, "new radar"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("stale")).isEqualTo(staleBefore + 3));
        verify(adapter, times(3)).indexDocument(any());
    }

    @Test
    void retriesTransientFailureAndCommitsOnlySuccessfulVersion() throws Exception {
        String id = UUID.randomUUID().toString();
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("temporary OpenSearch error");
            return null;
        }).when(adapter).indexDocument(argThat(doc -> doc.getId().equals(id)));
        send(event(id, "ENTITY_CREATED", 1, "retry radar"));
        assertThat(waitForVersion(id, 1).getStatus()).isEqualTo(IndexingStatus.INDEXED);
        assertThat(attempts).hasValue(2);
        verify(embeddings, times(2)).generateEmbedding("retry radar");
    }

    @Test
    void sendsInvalidMessageToDeadLetterTopicWithoutIndexing() throws Exception {
        try (var consumer = deadLetterConsumer()) {
            String invalid = "{\"eventType\":\"ENTITY_CREATED\",\"documentId\":\"invalid\"}";
            send(invalid);
            var record = KafkaTestUtils.getSingleRecord(consumer, "indexing-test-dlt", Duration.ofSeconds(20));
            assertThat(record.value()).isEqualTo(invalid);
            assertThat(repository.findByDocumentIdAndIndexName("invalid", "kafka-entities")).isEmpty();
            verifyNoInteractions(adapter, embeddings);
        }
    }

    @Test
    void sendsExhaustedFailureToDeadLetterTopicAndRollsBackState() throws Exception {
        String id = UUID.randomUUID().toString();
        doThrow(new IllegalStateException("persistent OpenSearch error"))
                .when(adapter).indexDocument(argThat(doc -> doc.getId().equals(id)));
        try (var consumer = deadLetterConsumer()) {
            String message = event(id, "ENTITY_CREATED", 1, "failed radar");
            send(message);
            var record = KafkaTestUtils.getSingleRecord(consumer, "indexing-test-dlt", Duration.ofSeconds(20));
            assertThat(record.value()).isEqualTo(message);
            verify(adapter, times(3)).indexDocument(argThat(doc -> doc.getId().equals(id)));
            assertThat(repository.findByDocumentIdAndIndexName(id, "kafka-entities")).isEmpty();
        }
    }

    private KafkaConsumer<String, String> deadLetterConsumer() {
        var properties = KafkaTestUtils.consumerProps(UUID.randomUUID().toString(), "false", broker);
        var consumer = new KafkaConsumer<>(properties, new StringDeserializer(), new StringDeserializer());
        broker.consumeFromAnEmbeddedTopic(consumer, true, "indexing-test-dlt");
        return consumer;
    }

    private void send(String message) throws Exception {
        template.send("indexing-test", "document-key", message).get(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private IndexingState waitForVersion(String id, long version) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(repository.findByDocumentIdAndIndexName(id, "kafka-entities"))
                        .hasValueSatisfying(state -> assertThat(state.getLastEventVersion()).isEqualTo(version)));
        return repository.findByDocumentIdAndIndexName(id, "kafka-entities").orElseThrow();
    }

    private double count(String outcome) {
        var counter = registry.find("search.kafka.events").tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private String event(String id, String type, long version, String text) {
        String data = text == null ? "" : ",\"data\":{\"title\":\"Radar\",\"searchText\":\"" + text + "\"}";
        return "{\"eventId\":\"" + id + "-v" + version + "\",\"eventType\":\"" + type
                + "\",\"documentId\":\"" + id + "\",\"version\":" + version + data + "}";
    }
}
