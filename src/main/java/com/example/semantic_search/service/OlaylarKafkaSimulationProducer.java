package com.example.semantic_search.service;

import com.example.semantic_search.model.SearchIndexingEvent;
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
 * {@code olaylar.json} dosyasındaki olayları simülasyon amacıyla Kafka {@code olaylar-events} konusuna
 * gerçek zamanlı akış (streaming) olarak basan üretici (producer) servisi.
 *
 * <p>Web arayüzünden veya REST API üzerinden başlatılıp durdurulabilir; gecikme süresi (delayMs)
 * ve gönderim limitleri dinamik olarak ayarlanabilir.</p>
 */
@Service
public class OlaylarKafkaSimulationProducer {

    private static final Logger log = LoggerFactory.getLogger(OlaylarKafkaSimulationProducer.class);

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
     * OlaylarKafkaSimulationProducer bileşenini yapılandıran yapıcı metot.
     *
     * @param objectMapper Jackson JSON dönüştürücüsü
     * @param bootstrapServers Kafka sunucu adresi
     * @param defaultTopic Varsayılan Kafka konusu
     */
    public OlaylarKafkaSimulationProducer(
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
     * @param limit Gönderilecek maksimum olay sayısı (0 veya daha küçükse dosyadaki tüm olaylar)
     * @param delayMs Olaylar arası bekleme süresi (milisaniye)
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
     * Çalışmakta olan Kafka olay simülasyonunu durdurur.
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
     * Kafka Producer ana döngüsü. {@code olaylar.json} dosyasını okur ve sırayla Kafka'ya gönderir.
     *
     * @param bootstrap Kafka bootstrap sunucuları
     * @param topic Hedef konu
     * @param limit Maksimum olay adedi
     * @param delayMs Gecikme süresi
     */
    private void runProducerLoop(String bootstrap, String topic, int limit, long delayMs) {
        File file = locateOlaylarFile();
        if (file == null) {
            log.error("olaylar.json dosyası bulunamadı! Simülasyon iptal edildi.");
            lastStatus = "ERROR: olaylar.json bulunamadı";
            running.set(false);
            return;
        }

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props);
             InputStream is = new FileInputStream(file)) {

            JsonNode root = objectMapper.readTree(is);
            if (!root.isArray()) {
                log.error("olaylar.json geçerli bir dizi içermiyor!");
                lastStatus = "ERROR: Geçersiz JSON formatı";
                running.set(false);
                return;
            }

            int total = (limit > 0) ? Math.min(limit, root.size()) : root.size();
            log.info("Kafka Simülasyonu BAŞLADI: {} olay, konu='{}', gecikme={}ms", total, topic, delayMs);

            for (int i = 0; i < total && running.get(); i++) {
                JsonNode rawDoc = root.get(i);
                String docId = rawDoc.path("entityId").asText(UUID.randomUUID().toString());

                ObjectNode envelope = objectMapper.createObjectNode();
                envelope.put("eventId", UUID.randomUUID().toString());
                envelope.put("eventType", "IncidentCreated");
                envelope.put("documentId", docId);
                envelope.put("version", 1L);
                envelope.set("data", rawDoc);

                String payload = objectMapper.writeValueAsString(envelope);

                ProducerRecord<String, String> record = new ProducerRecord<>(topic, docId, payload);
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        log.warn("Kafka gönderim hatası (docId={}): {}", docId, exception.getMessage());
                    }
                });

                publishedCount.incrementAndGet();
                String title = rawDoc.path("title").asText("Başlıksız Olay");
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
     * {@code olaylar.json} dosyasını diskte arar.
     *
     * @return File nesnesi veya null
     */
    private File locateOlaylarFile() {
        Path p1 = Paths.get("src/main/java/com/example/semantic_search/olaylar.json");
        if (Files.exists(p1)) return p1.toFile();

        Path p2 = Paths.get("olaylar.json");
        if (Files.exists(p2)) return p2.toFile();

        Path p3 = Paths.get("src/main/resources/olaylar.json");
        if (Files.exists(p3)) return p3.toFile();

        return null;
    }

    /**
     * Simülasyon durum bilgilerini istemciye aktaran kayıt sınıfı.
     *
     * @param running Çalışıyor mu
     * @param publishedCount Gönderilen mesaj adedi
     * @param targetLimit Hedeflenen toplam adet
     * @param delayMs Gecikme süresi
     * @param topic Kafka konusu
     * @param bootstrapServers Kafka sunucuları
     * @param status Durum metni
     * @param lastEventSummary Son iletilen olay özeti
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
