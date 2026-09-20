package com.example.semantic_search.kafka;

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

@Validated
@ConfigurationProperties("search.kafka")
public class KafkaIndexingProperties {

    @NotEmpty
    private List<@NotBlank String> topics = List.of();
    @NotEmpty
    private Map<@NotBlank String, @NotNull @Valid EventRoute> routes = new LinkedHashMap<>();
    @NotBlank
    private String deadLetterTopic = "search.indexing.errors";
    @Min(0)
    private int retryAttempts = 2;
    @Min(0)
    private long retryDelayMs = 1000;
    @Min(1)
    private long publishTimeoutMs = 10000;
    @Min(1)
    private int concurrency = 1;

    public enum Operation { UPSERT, DELETE }

    @AssertTrue(message = "Dead-letter topic must not be one of the source topics")
    public boolean isDeadLetterTopicSeparate() { return !topics.contains(deadLetterTopic); }

    public record EventRoute(@NotNull Operation operation,
                             @NotBlank String indexName,
                             @NotBlank String documentType) { }

    public List<String> getTopics() { return topics; }
    public void setTopics(List<String> topics) { this.topics = topics; }
    public Map<String, EventRoute> getRoutes() { return routes; }
    public void setRoutes(Map<String, EventRoute> routes) { this.routes = routes; }
    public String getDeadLetterTopic() { return deadLetterTopic; }
    public void setDeadLetterTopic(String deadLetterTopic) { this.deadLetterTopic = deadLetterTopic; }
    public int getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }
    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    public long getPublishTimeoutMs() { return publishTimeoutMs; }
    public void setPublishTimeoutMs(long publishTimeoutMs) { this.publishTimeoutMs = publishTimeoutMs; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
}
