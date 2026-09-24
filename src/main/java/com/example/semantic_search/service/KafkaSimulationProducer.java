package com.example.semantic_search.service;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Airgap ve test ortamlarında veri akışını simüle etmek amacıyla JSON veri dosyalarındaki kayıtları
 * Kafka konularına gerçek zamanlı streaming olarak basan jenerik üretici (producer) servisi.
 */
@Service
public class KafkaSimulationProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaSimulationProducer.class);

    private final ObjectMapper objectMapper;
    private final String bootstrapServers;
    private final String defaultTopic;

    // Canlı durum takibi
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger publishedCount = new AtomicInteger(0);
    private final AtomicInteger targetLimit = new AtomicInteger(0);
    private volatile long currentDelayMs = 50;
    private volatile String lastStatus = "IDLE";
    private volatile String lastEventSummary = null;

    /**
     * KafkaSimulationProducer bileşenini yapılandıran yapıcı metot.
     *
     * @param objectMapper Jackson JSON dönüştürücüsü
     * @param bootstrapServers Kafka sunucu adresi
     * @param defaultTopic Varsayılan Kafka konusu
     */
    @Autowired
    public KafkaSimulationProducer(
            ObjectMapper objectMapper,
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${search.kafka.topics:olaylar-events}") String defaultTopic) {
        this.objectMapper = objectMapper;
        this.bootstrapServers = bootstrapServers;
        this.defaultTopic = defaultTopic.split(",")[0].trim();
    }

    /**
     * Arka plan iş parçacığında asenkron canlı simülasyonu başlatır.
     *
     * @param limit Gönderilecek maksimum olay sayısı (0 veya daha küçükse dosyadaki tüm kayıtlar)
     * @param delayMs Kayıtlar arası bekleme süresi (milisaniye)
     * @return Simülasyon başarıyla başlatıldıysa true, zaten çalışıyorsa false
     */
    public synchronized boolean startSimulation(int limit, long delayMs) {
        if (running.get()) {
            log.warn("Simülasyon zaten çalışıyor!");
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
     * Çalışmakta olan Kafka veri simülasyonunu durdurur.
     */
    public synchronized void stopSimulation() {
        if (running.get()) {
            running.set(false);
            lastStatus = "STOPPED";
            log.info("Simülasyon durdurma istendi. Şu ana kadar gönderilen: {}", publishedCount.get());
        }
    }

    /**
     * Simülasyonun anlık çalışma durumunu, gönderilen mesaj sayısını ve son durumu döner.
     *
     * @return Simülasyon durumu DTO'su
     */
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

    /**
     * Kafka Producer ana döngüsü. JSON dosyasını okur ve sırayla Kafka'ya gönderir.
     *
     * @param bootstrap Kafka bootstrap sunucuları
     * @param topic Hedef konu
     * @param limit Maksimum kayıt adedi
     * @param delayMs Gecikme süresi
     */
    private void runProducerLoop(String bootstrap, String topic, int limit, long delayMs) {
        File file = locateDataFile();
        if (file == null) {
            log.error("Veri dosyası (data.json / dataset.json / olaylar.json) bulunamadı! Simülasyon iptal edildi.");
            lastStatus = "ERROR: Veri dosyası bulunamadı";
            running.set(false);
            return;
        }

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "5000");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props);
             InputStream is = new FileInputStream(file)) {

            JsonNode root = objectMapper.readTree(is);
            if (!root.isArray()) {
                log.error("Veri dosyası geçerli bir JSON dizisi içermiyor!");
                lastStatus = "ERROR: Geçersiz JSON formatı";
                running.set(false);
                return;
            }

            int total = (limit > 0) ? Math.min(limit, root.size()) : root.size();
            log.info("Kafka Simülasyonu BAŞLADI: {} kayıt, konu='{}', gecikme={}ms", total, topic, delayMs);

            for (int i = 0; i < total && running.get(); i++) {
                JsonNode rawDoc = root.get(i);
                String docId = rawDoc.path("entityId").asText(
                        rawDoc.path("id").asText(
                                rawDoc.path("documentId").asText(UUID.randomUUID().toString())));

                String eventType = rawDoc.path("entityType").asText(
                        rawDoc.path("type").asText(
                                rawDoc.path("category").asText("DATA_EVENT")));

                long version = rawDoc.path("version").asLong(1L);

                ObjectNode envelope = objectMapper.createObjectNode();
                envelope.put("eventId", UUID.randomUUID().toString());
                envelope.put("eventType", eventType);
                envelope.put("documentId", docId);
                envelope.put("version", version);
                envelope.set("data", rawDoc);

                String payload = objectMapper.writeValueAsString(envelope);

                ProducerRecord<String, String> record = new ProducerRecord<>(topic, docId, payload);
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        log.warn("Kafka gönderim hatası (docId={}): {}", docId, exception.getMessage());
                    }
                });

                publishedCount.incrementAndGet();
                String title = rawDoc.path("title").asText(rawDoc.path("name").asText("Kayıt"));
                lastEventSummary = String.format("[%d/%d] %s (%s)", publishedCount.get(), total, title, docId);

                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            producer.flush();
            if (running.get()) {
                lastStatus = "COMPLETED";
                log.info("Kafka Simülasyonu TAMAMLANDI. Toplam gönderilen: {}", publishedCount.get());
            }

        } catch (Exception e) {
            log.error("Simülasyon döngüsü istisna ile sonlandı: {}", e.getMessage(), e);
            lastStatus = "ERROR: " + e.getMessage();
        } finally {
            running.set(false);
        }
    }

    /**
     * Veri dosyasını diskte arar.
     *
     * @return File nesnesi veya null
     */
    private File locateDataFile() {
        String[] candidateNames = {"data.json", "dataset.json", "olaylar.json"};
        String[] candidateDirs = {
                "",
                "src/main/resources/",
                "src/main/java/com/example/semantic_search/"
        };

        for (String name : candidateNames) {
            for (String dir : candidateDirs) {
                Path p = Paths.get(dir + name);
                if (Files.exists(p)) return p.toFile();
            }
        }

        return null;
    }

    /**
     * Simülasyon durum bilgilerini istemciye aktaran kayıt sınıfı.
     */
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
}
