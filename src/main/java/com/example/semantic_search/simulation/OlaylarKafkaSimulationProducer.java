package com.example.semantic_search.simulation;

import com.example.semantic_search.kafka.SearchIndexingEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Live Kafka simulation producer for incident events.
 * Reads {@code olaylar.json} and streams events into Kafka topic {@code olaylar-events}.
 * 
 * Can be executed as:
 * 1. A standalone Java script via {@code main(String[] args)}
 * 2. A Spring-managed service controlled via REST/UI
 */
@Service
public class OlaylarKafkaSimulationProducer {

    private static final Logger log = LoggerFactory.getLogger(OlaylarKafkaSimulationProducer.class);

    private final ObjectMapper objectMapper;
    private final String bootstrapServers;
    private final String defaultTopic;

    // Live state tracking
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger publishedCount = new AtomicInteger(0);
    private final AtomicInteger targetLimit = new AtomicInteger(0);
    private volatile long currentDelayMs = 50;
    private volatile String lastStatus = "IDLE";
    private volatile String lastEventSummary = null;

    public OlaylarKafkaSimulationProducer(
            ObjectMapper objectMapper,
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${search.kafka.topics:olaylar-events}") String defaultTopic) {
        this.objectMapper = objectMapper;
        this.bootstrapServers = bootstrapServers;
        this.defaultTopic = defaultTopic.split(",")[0].trim();
    }

    /**
     * Start live simulation asynchronously in a background thread.
     */
    public synchronized boolean startSimulation(int limit, long delayMs) {
        if (running.get()) {
            log.warn("Simulation is already running!");
            return false;
        }

        running.set(true);
        publishedCount.set(0);
        targetLimit.set(limit);
        this.currentDelayMs = delayMs;
        lastStatus = "RUNNING";

        CompletableFuture.runAsync(() -> runProducerLoop(bootstrapServers, defaultTopic, limit, delayMs));
        return true;
    }

    /**
     * Stop currently running simulation.
     */
    public synchronized void stopSimulation() {
        if (running.get()) {
            running.set(false);
            lastStatus = "STOPPED";
            log.info("Simulation stop requested. Published so far: {}", publishedCount.get());
        }
    }

    public SimulationStatus getStatus() {
        return new SimulationStatus(
                running.get(),
                publishedCount.get(),
                targetLimit.get(),
                currentDelayMs,
                defaultTopic,
                bootstrapServers,
                lastStatus,
                lastEventSummary
        );
    }

    private void runProducerLoop(String servers, String topic, int limit, long delayMs) {
        log.info("Starting Kafka simulation: target={} events, delay={}ms, topic={}, servers={}",
                limit, delayMs, topic, servers);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000); // 5 sec timeout if Kafka is unreachable

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props);
             InputStream is = findOlaylarJsonStream()) {

            if (is == null) {
                lastStatus = "ERROR: olaylar.json dosyası bulunamadı!";
                log.error("[Kafka Simulation] olaylar.json not found!");
                running.set(false);
                return;
            }

            JsonNode root = objectMapper.readTree(is);
            if (!root.isArray()) {
                lastStatus = "ERROR: olaylar.json bir dizi (array) içermiyor!";
                running.set(false);
                return;
            }

            int totalAvailable = root.size();
            int target = Math.min(limit, totalAvailable);
            log.info("[Kafka Simulation] Starting live stream: target={} events (total available: {})", target, totalAvailable);

            for (int i = 0; i < totalAvailable && running.get(); i++) {
                if (publishedCount.get() >= target) {
                    break;
                }

                JsonNode item = root.get(i);
                String entityId = item.has("entityId") ? item.get("entityId").asText() : UUID.randomUUID().toString();
                long version = item.has("version") ? item.get("version").asLong() : 1L;
                String title = item.has("title") ? item.get("title").asText() : "Olay Raporu";

                // Create SearchIndexingEvent wire contract
                ObjectNode dataNode = item.isObject() ? (ObjectNode) item.deepCopy() : objectMapper.createObjectNode();
                dataNode.put("id", entityId);

                // Ensure search text is present
                if (!dataNode.has("searchText")) {
                    StringBuilder sb = new StringBuilder();
                    if (item.has("title")) sb.append(item.get("title").asText()).append("\n");
                    if (item.has("shortText")) sb.append(item.get("shortText").asText()).append("\n");
                    if (item.has("longText")) sb.append(item.get("longText").asText());
                    dataNode.put("searchText", sb.toString().trim());
                }

                SearchIndexingEvent event = new SearchIndexingEvent(
                        UUID.randomUUID().toString(),
                        "OLAY",
                        entityId,
                        version,
                        dataNode
                );

                String eventJson = objectMapper.writeValueAsString(event);
                producer.send(new ProducerRecord<>(topic, entityId, eventJson), (recordMetadata, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka Simulation] Send failed for {}: {}", entityId, ex.getMessage());
                        lastStatus = "ERROR: " + ex.getMessage();
                        running.set(false);
                    }
                });

                int count = publishedCount.incrementAndGet();
                lastEventSummary = String.format("#%d: %s", count, title);

                if (count % 50 == 0 || count == target) {
                    log.info("[Kafka Simulation] Published {}/{} events to topic '{}'", count, target, topic);
                }

                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            producer.flush();
            if (!lastStatus.startsWith("ERROR")) {
                lastStatus = publishedCount.get() >= target ? "COMPLETED" : "STOPPED";
            }
            log.info("[Kafka Simulation] Finished. Total events published: {}, status: {}", publishedCount.get(), lastStatus);

        } catch (Exception e) {
            log.error("[Kafka Simulation] Failure during event publishing: {}", e.getMessage(), e);
            lastStatus = "ERROR: Kafka bağlantı hatası (" + e.getMessage() + ")";
        } finally {
            running.set(false);
        }
    }

    private static InputStream findOlaylarJsonStream() {
        String[] candidates = {
                "src/main/java/com/example/semantic_search/olaylar.json",
                "olaylar.json",
                "../src/main/java/com/example/semantic_search/olaylar.json"
        };
        for (String pathStr : candidates) {
            Path p = Paths.get(pathStr);
            if (Files.exists(p)) {
                try {
                    log.info("Found olaylar.json at: {}", p.toAbsolutePath());
                    return new FileInputStream(p.toFile());
                } catch (Exception ignored) {}
            }
        }
        InputStream is = OlaylarKafkaSimulationProducer.class.getResourceAsStream("/olaylar.json");
        if (is != null) return is;
        is = OlaylarKafkaSimulationProducer.class.getResourceAsStream("/com/example/semantic_search/olaylar.json");
        return is;
    }

    public record SimulationStatus(
            boolean running,
            int publishedCount,
            int targetLimit,
            long delayMs,
            String topic,
            String bootstrapServers,
            String status,
            String lastEventSummary
    ) {}

    // ──────────────────────────────────────────────────────────────────────────
    // Standalone CLI Runner (main)
    // ──────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        int limit = 500;
        long delayMs = 50;
        String topic = "olaylar-events";
        String servers = "localhost:9092";

        for (int i = 0; i < args.length; i++) {
            if ("--limit".equals(args[i]) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if ("--delay".equals(args[i]) && i + 1 < args.length) {
                delayMs = Long.parseLong(args[++i]);
            } else if ("--topic".equals(args[i]) && i + 1 < args.length) {
                topic = args[++i];
            } else if ("--servers".equals(args[i]) && i + 1 < args.length) {
                servers = args[++i];
            }
        }

        System.out.println("==========================================================");
        System.out.println("🚀 Olaylar Kafka Simulation Producer (Standalone Java Script)");
        System.out.println("==========================================================");
        System.out.printf("  Broker: %s\n  Topic:  %s\n  Limit:  %d events\n  Delay:  %d ms (~%d events/sec)\n",
                servers, topic, limit, delayMs, delayMs > 0 ? 1000 / delayMs : limit);
        System.out.println("==========================================================");

        OlaylarKafkaSimulationProducer producer = new OlaylarKafkaSimulationProducer(new ObjectMapper(), servers, topic);
        producer.runProducerLoop(servers, topic, limit, delayMs);

        System.out.println("✅ Simulation completed successfully.");
    }
}
