package com.example.semantic_search.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.List;
import java.util.Locale;

public class KafkaIndexingListener {

    private final SearchEventProcessor processor;
    private final KafkaIndexingProperties properties;
    private final MeterRegistry meterRegistry;

    public KafkaIndexingListener(SearchEventProcessor processor, KafkaIndexingProperties properties,
                                 MeterRegistry meterRegistry) {
        this.processor = processor;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public List<String> topics() { return properties.getTopics(); }

    @KafkaListener(id = "search-indexer", topics = "#{__listener.topics()}",
            groupId = "${spring.kafka.consumer.group-id}", containerFactory = "searchKafkaListenerContainerFactory")
    public void listen(ConsumerRecord<String, String> record) {
        SearchEventProcessor.Outcome outcome = processor.process(record.value());
        meterRegistry.counter("search.kafka.events", "outcome", outcome.name().toLowerCase(Locale.ROOT)).increment();
    }
}
