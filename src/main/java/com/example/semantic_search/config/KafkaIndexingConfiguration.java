package com.example.semantic_search.config;

import com.example.semantic_search.exception.InvalidSearchEventException;
import com.example.semantic_search.kafka.JsonSearchEventMapper;
import com.example.semantic_search.kafka.KafkaIndexingListener;
import com.example.semantic_search.kafka.SearchEventMapper;
import com.example.semantic_search.repository.IndexingStateRepository;
import com.example.semantic_search.service.IndexingService;
import com.example.semantic_search.service.SearchEventProcessor;
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

/**
 * Kafka tabanlı asenkron indeksleme dinleyicilerini, hata işleyicilerini (Error Handler)
 * ve Dead-Letter Topic (DLT) kurtarma bileşenlerini başlatan Spring yapılandırması.
 *
 * <p>Sadece {@code search.kafka.enabled=true} olduğunda devreye girer. Hatalı mesajları
 * belirlenen bekleme ve deneme sayısı sonrasında DLT kuyruğuna aktararak veri kaybını önler.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "search.kafka.enabled", havingValue = "true")
@EnableConfigurationProperties(KafkaIndexingProperties.class)
@EnableKafka
public class KafkaIndexingConfiguration {

    /**
     * Kafka olaylarını ham JSON'dan dahili nesnelere çeviren haritalayıcıyı oluşturur.
     *
     * @param mapper Jackson ObjectMapper nesnesi
     * @return Olay haritalayıcı bileşeni
     */
    @Bean
    @ConditionalOnMissingBean(SearchEventMapper.class)
    public SearchEventMapper searchEventMapper(ObjectMapper mapper) {
        return new JsonSearchEventMapper(mapper);
    }

    /**
     * Kafka'dan gelen mesajları doğrulayan ve indeksleme servisine aktaran işlemciyi oluşturur.
     *
     * @param mapper Olay haritalayıcı
     * @param properties Kafka ayarları
     * @param indexingService İndeksleme iş servisi
     * @param repository İndeksleme durum repository'si
     * @param validator Bean doğrulayıcısı
     * @return Arama olay işlemcisi
     */
    @Bean
    public SearchEventProcessor searchEventProcessor(SearchEventMapper mapper,
                                                      KafkaIndexingProperties properties,
                                                      IndexingService indexingService,
                                                      IndexingStateRepository repository,
                                                      Validator validator) {
        return new SearchEventProcessor(mapper, properties, indexingService, repository, validator);
    }

    /**
     * Kafka dinleyici bileşenini oluşturur.
     *
     * @param processor Olay işlemcisi
     * @param properties Kafka ayarları
     * @param registry Micrometer metrik kayıtçısı
     * @return KafkaIndexingListener bileşeni
     */
    @Bean
    public KafkaIndexingListener kafkaIndexingListener(SearchEventProcessor processor,
                                                       KafkaIndexingProperties properties,
                                                       MeterRegistry registry) {
        return new KafkaIndexingListener(processor, properties, registry);
    }

    /**
     * Kafka tüketim hatalarını yakalayan, yeniden deneyen ve kurtarılamayanları DLT'ye gönderen hata yöneticisi.
     *
     * @param template KafkaTemplate nesnesi
     * @param properties Kafka ayarları
     * @param registry Micrometer metrik kayıtçısı
     * @return DefaultErrorHandler nesnesi
     */
    @Bean
    public DefaultErrorHandler searchKafkaErrorHandler(KafkaTemplate<Object, Object> template,
                                                        KafkaIndexingProperties properties,
                                                        MeterRegistry registry) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(properties.getDeadLetterTopic(), -1));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofMillis(properties.getPublishTimeoutMs()));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            recoverer.accept(record, exception);
            registry.counter("search.kafka.events", "outcome", "dead-letter").increment();
        }, new FixedBackOff(properties.getRetryDelayMs(), properties.getRetryAttempts()));

        errorHandler.addNotRetryableExceptions(InvalidSearchEventException.class);
        return errorHandler;
    }

    /**
     * Eşzamanlı mesaj tüketimini ve RECORD seviyesinde commit onayını yöneten container fabrikası.
     *
     * @param consumerFactory Kafka tüketici fabrikası
     * @param properties Kafka ayarları
     * @param searchKafkaErrorHandler Hata yöneticisi
     * @return ConcurrentKafkaListenerContainerFactory nesnesi
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> searchKafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaIndexingProperties properties,
            DefaultErrorHandler searchKafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(properties.getConcurrency());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(searchKafkaErrorHandler);
        return factory;
    }
}
