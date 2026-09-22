package com.example.semantic_search.client.opensearch;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator ile entegre çalışan OpenSearch sağlık göstergesi bileşeni.
 *
 * <p>Uygulamanın {@code /actuator/health} uç noktasında OpenSearch kümesinin
 * ayakta olup olmadığını raporlar.</p>
 */
@Component
public class OpenSearchHealthIndicator implements HealthIndicator {

    private final OpenSearchAdapter adapter;

    /**
     * OpenSearchAdapter bağımlılığını enjekte eden yapıcı metot.
     *
     * @param adapter OpenSearch istemci adaptörü
     */
    public OpenSearchHealthIndicator(OpenSearchAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * OpenSearch kümesinin sağlık durumunu kontrol eder.
     *
     * @return OpenSearch erişilebilir ise Health.up(), değilse Health.down()
     */
    @Override
    public Health health() {
        if (adapter.isHealthy()) {
            return Health.up().withDetail("engine", "OpenSearch").build();
        }
        return Health.down().withDetail("engine", "OpenSearch").build();
    }
}
