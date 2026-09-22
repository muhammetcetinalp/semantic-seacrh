package com.example.semantic_search.kafka;

import com.example.semantic_search.config.KafkaIndexingProperties;
import com.example.semantic_search.service.SearchEventProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.List;
import java.util.Locale;

/**
 * Kafka indeksleme konularını (topics) dinleyen ve gelen mesajları
 * asenkron indeksleme işlemcisine ({@link SearchEventProcessor}) aktaran tüketici (consumer) bileşeni.
 *
 * <p>İşlem sonuçlarına göre Micrometer metrik sayaçlarını günceller.</p>
 */
public class KafkaIndexingListener {

    private final SearchEventProcessor processor;
    private final KafkaIndexingProperties properties;
    private final MeterRegistry meterRegistry;

    /**
     * Gerekli bağımlılıkları enjekte eden yapıcı metot.
     *
     * @param processor Olay işleme servisi
     * @param properties Kafka indeksleme yapılandırma parametreleri
     * @param meterRegistry Micrometer metrik kayıt defteri
     */
    public KafkaIndexingListener(SearchEventProcessor processor, KafkaIndexingProperties properties,
                                 MeterRegistry meterRegistry) {
        this.processor = processor;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Dinlenecek Kafka konu isimleri listesini döner.
     *
     * @return Konu isimleri listesi
     */
    public List<String> topics() {
        return properties.getTopics();
    }

    /**
     * Belirlenen Kafka konularından gelen kayıtları dinler, işler ve metrikleri artırır.
     *
     * @param record Kafka tüketici kaydı (ConsumerRecord)
     */
    @KafkaListener(id = "search-indexer", topics = "#{__listener.topics()}",
            groupId = "${spring.kafka.consumer.group-id}", containerFactory = "searchKafkaListenerContainerFactory")
    public void listen(ConsumerRecord<String, String> record) {
        SearchEventProcessor.Outcome outcome = processor.process(record.value());
        meterRegistry.counter("search.kafka.events", "outcome", outcome.name().toLowerCase(Locale.ROOT)).increment();
    }
}
