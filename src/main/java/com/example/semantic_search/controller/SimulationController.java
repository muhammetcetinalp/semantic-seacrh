package com.example.semantic_search.controller;

import com.example.semantic_search.repository.IndexingStateRepository;
import com.example.semantic_search.service.KafkaSimulationProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Kafka canlı olay simülasyonunu başlatma, durdurma ve anlık durumunu izleme
 * işlevlerini sunan REST denetleyicisi.
 */
@RestController
@RequestMapping("/api/v1/simulation/kafka")
@CrossOrigin
public class SimulationController {

    private final KafkaSimulationProducer producer;
    private final IndexingStateRepository indexingStateRepository;

    /**
     * SimulationController bağımlılıklarını enjekte eden yapıcı metot.
     *
     * @param producer Kafka olay simülasyon üreticisi
     * @param indexingStateRepository Veritabanı durum tablosu deposu
     */
    @Autowired
    public SimulationController(
            KafkaSimulationProducer producer,
            IndexingStateRepository indexingStateRepository) {
        this.producer = producer;
        this.indexingStateRepository = indexingStateRepository;
    }

    /**
     * Kafka olay akış simülasyonunu arka planda başlatır.
     *
     * @param limit Gönderilecek olay adedi (varsayılan: 1000)
     * @param delayMs Olaylar arası bekleme milisaniyesi (varsayılan: 50)
     * @return Başlatma durumu haritası
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(
            @RequestParam(defaultValue = "1000") int limit,
            @RequestParam(defaultValue = "50") long delayMs) {
        boolean started = producer.startSimulation(limit, delayMs);
        return ResponseEntity.ok(Map.of(
                "started", started,
                "limit", limit,
                "delayMs", delayMs,
                "message", started ? "Kafka simülasyonu başlatıldı." : "Simülasyon zaten çalışıyor."
        ));
    }

    /**
     * Devam eden Kafka simülasyonunu durdurur.
     *
     * @return Durduruldu onay mesajı
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        producer.stopSimulation();
        return ResponseEntity.ok(Map.of(
                "stopped", true,
                "message", "Kafka simülasyonu durduruldu."
        ));
    }

    /**
     * Simülasyonun anlık durumu, gönderilen mesaj adedi ve PostgreSQL'e yazılan kayıt sayısını döner.
     *
     * @return Canlı metrikler ve durum haritası
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        var status = producer.getStatus();
        long dbRecordedCount = 0;
        try {
            dbRecordedCount = indexingStateRepository.count();
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
                "running", status.running(),
                "publishedCount", status.publishedCount(),
                "targetLimit", status.targetLimit(),
                "delayMs", status.delayMs(),
                "topic", status.topic() != null ? status.topic() : "",
                "bootstrapServers", status.bootstrapServers() != null ? status.bootstrapServers() : "",
                "status", status.status() != null ? status.status() : "IDLE",
                "lastEventSummary", status.lastEventSummary() != null ? status.lastEventSummary() : "",
                "dbRecordedCount", dbRecordedCount
        ));
    }
}
