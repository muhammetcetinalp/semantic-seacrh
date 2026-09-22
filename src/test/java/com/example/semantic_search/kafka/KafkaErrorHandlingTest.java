package com.example.semantic_search.kafka;

import com.example.semantic_search.config.KafkaIndexingConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KafkaErrorHandlingTest {
    @Test
    @SuppressWarnings("unchecked")
    void failedDeadLetterPublicationDoesNotRecoverTheRecord() {
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        var registry = new SimpleMeterRegistry();
        var handler = new KafkaIndexingConfiguration().searchKafkaErrorHandler(
                template, new KafkaIndexingProperties(), registry);
        Consumer<?, ?> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        var record = new ConsumerRecord<>("source", 0, 0L, "doc1", "invalid-json");

        assertThat(handler.handleOne(new InvalidSearchEventException("invalid"), record, consumer, container))
                .isFalse();
        verify(template).send(any(ProducerRecord.class));
        assertThat(registry.find("search.kafka.events").tag("outcome", "dead-letter").counter()).isNull();
        verifyNoInteractions(consumer);
    }
}
