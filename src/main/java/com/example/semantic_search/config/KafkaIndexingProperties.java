package com.example.semantic_search.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka üzerinden asenkron doküman indeksleme boru hattına ait yapılandırma özellikleri.
 *
 * <p>application.yml dosyasındaki {@code search.kafka} önekini doğrulamalı (Bean Validation) olarak bağlar.
 * Dinlenecek topic listesi, topic-indeks yönlendirme kuralları (routes), hata durumunda gönderilecek
 * Dead-Letter Topic (DLT) adı ve yeniden deneme (retry) politikalarını yönetir.</p>
 */
@Validated
@ConfigurationProperties("search.kafka")
public class KafkaIndexingProperties {

    /** Dinlenecek Kafka topic isimleri listesi. */
    @NotEmpty
    private List<@NotBlank String> topics = List.of();

    /** Olay tipine göre indeks ve doküman tipi yönlendirme haritası. */
    @NotEmpty
    private Map<@NotBlank String, @NotNull @Valid EventRoute> routes = new LinkedHashMap<>();

    /** Hatalı ve kurtarılamayan olayların gönderileceği Dead-Letter Topic adı. */
    @NotBlank
    private String deadLetterTopic = "search.indexing.errors";

    /** Hata durumunda yapılacak yeniden deneme sayısı. */
    @Min(0)
    private int retryAttempts = 2;

    /** Yeniden denemeler arasındaki bekleme süresi (milisaniye). */
    @Min(0)
    private long retryDelayMs = 1000;

    /** Kafka'ya mesaj gönderme zaman aşımı süresi (milisaniye). */
    @Min(1)
    private long publishTimeoutMs = 10000;

    /** Kafka dinleyici kapsayıcısının paralel iş parçacığı (thread) sayısı. */
    @Min(1)
    private int concurrency = 1;

    /**
     * Desteklenen olay işlem türleri.
     */
    public enum Operation {
        /** Dokümanı ekle veya varsa güncelle. */
        UPSERT,
        /** Dokümanı indeksten sil. */
        DELETE
    }

    /**
     * Dead-Letter Topic adının ana kaynak topic'lerden farklı olduğunu denetler.
     *
     * @return DLT ana topic'lerden farklıysa true
     */
    @AssertTrue(message = "Dead-letter topic ana dinlenen topic'lerden biri olamaz")
    public boolean isDeadLetterTopicSeparate() {
        return !topics.contains(deadLetterTopic);
    }

    /**
     * Tek bir olay tipi için yönlendirme kuralını temsil eden kayıt.
     *
     * @param operation Yapılacak işlem (UPSERT veya DELETE)
     * @param indexName Hedef OpenSearch indeksi
     * @param documentType Hedef doküman tipi
     */
    public record EventRoute(
            @NotNull Operation operation,
            @NotBlank String indexName,
            @NotBlank String documentType) { }

    /** @return Dinlenen topic'ler */
    public List<String> getTopics() { return topics; }
    /** @param topics Dinlenen topic'ler */
    public void setTopics(List<String> topics) { this.topics = topics; }

    /** @return Yönlendirme haritası */
    public Map<String, EventRoute> getRoutes() { return routes; }
    /** @param routes Yönlendirme haritası */
    public void setRoutes(Map<String, EventRoute> routes) { this.routes = routes; }

    /** @return DLT topic adı */
    public String getDeadLetterTopic() { return deadLetterTopic; }
    /** @param deadLetterTopic DLT topic adı */
    public void setDeadLetterTopic(String deadLetterTopic) { this.deadLetterTopic = deadLetterTopic; }

    /** @return Yeniden deneme sayısı */
    public int getRetryAttempts() { return retryAttempts; }
    /** @param retryAttempts Yeniden deneme sayısı */
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

    /** @return Deneme gecikmesi (ms) */
    public long getRetryDelayMs() { return retryDelayMs; }
    /** @param retryDelayMs Deneme gecikmesi (ms) */
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }

    /** @return Gönderme zaman aşımı (ms) */
    public long getPublishTimeoutMs() { return publishTimeoutMs; }
    /** @param publishTimeoutMs Gönderme zaman aşımı (ms) */
    public void setPublishTimeoutMs(long publishTimeoutMs) { this.publishTimeoutMs = publishTimeoutMs; }

    /** @return Eşzamanlı tüketici sayısı */
    public int getConcurrency() { return concurrency; }
    /** @param concurrency Eşzamanlı tüketici sayısı */
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
}
