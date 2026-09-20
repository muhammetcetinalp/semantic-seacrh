package com.example.semantic_search.kafka;

import com.example.semantic_search.indexing.IndexingService;
import com.example.semantic_search.indexing.IndexingStateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Validator;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "search.kafka.enabled", havingValue = "true")
@EnableConfigurationProperties(KafkaIndexingProperties.class)
@EnableKafka
public class KafkaIndexingConfiguration {

    @Bean
    @ConditionalOnMissingBean(SearchEventMapper.class)
    public SearchEventMapper searchEventMapper(ObjectMapper mapper) { return new JsonSearchEventMapper(mapper); }

    @Bean
    public SearchEventProcessor searchEventProcessor(SearchEventMapper mapper, KafkaIndexingProperties properties,
                                                      IndexingService indexingService,
                                                      IndexingStateRepository repository, Validator validator) {
        return new SearchEventProcessor(mapper, properties, indexingService, repository, validator);
    }

    @Bean
    public KafkaIndexingListener kafkaIndexingListener(SearchEventProcessor processor,
                                                       KafkaIndexingProperties properties, MeterRegistry registry) {
        return new KafkaIndexingListener(processor, properties, registry);
    }

    @Bean
    public DefaultErrorHandler searchKafkaErrorHandler(KafkaTemplate<Object, Object> template,
            KafkaIndexingProperties properties, MeterRegistry registry) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(properties.getDeadLetterTopic(), -1));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofMillis(properties.getPublishTimeoutMs()));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            // If publishing fails, this throws and the failed record remains uncommitted.
            recoverer.accept(record, exception);
            registry.counter("search.kafka.events", "outcome", "dead-letter").increment();
        }, new FixedBackOff(properties.getRetryDelayMs(), properties.getRetryAttempts()));
        errorHandler.addNotRetryableExceptions(InvalidSearchEventException.class);
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> searchKafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory, KafkaIndexingProperties properties,
            DefaultErrorHandler searchKafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(properties.getConcurrency());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(searchKafkaErrorHandler);
        return factory;
    }
}
