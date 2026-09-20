package com.example.semantic_search.opensearch;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class OpenSearchHealthIndicator implements HealthIndicator {

    private final OpenSearchAdapter adapter;

    public OpenSearchHealthIndicator(OpenSearchAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public Health health() {
        if (adapter.isHealthy()) {
            return Health.up().withDetail("engine", "OpenSearch").build();
        }
        return Health.down().withDetail("engine", "OpenSearch").build();
    }
}
