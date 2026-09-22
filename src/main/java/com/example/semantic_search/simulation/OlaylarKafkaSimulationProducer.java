package com.example.semantic_search.simulation;

/**
 * Olaylar Kafka simülasyon üreticisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.service.OlaylarKafkaSimulationProducer} paketine taşınmıştır.
 */
@Deprecated
public class OlaylarKafkaSimulationProducer extends com.example.semantic_search.service.OlaylarKafkaSimulationProducer {

    public OlaylarKafkaSimulationProducer(tools.jackson.databind.ObjectMapper objectMapper,
                                          String bootstrapServers,
                                          String defaultTopic) {
        super(objectMapper, bootstrapServers, defaultTopic);
    }
}
