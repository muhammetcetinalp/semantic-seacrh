package com.example.semantic_search.simulation;

import com.example.semantic_search.indexing.IndexingStateRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/simulation/kafka")
public class SimulationController {

    private final OlaylarKafkaSimulationProducer producer;
    private final IndexingStateRepository indexingStateRepository;

    public SimulationController(
            OlaylarKafkaSimulationProducer producer,
            IndexingStateRepository indexingStateRepository) {
        this.producer = producer;
        this.indexingStateRepository = indexingStateRepository;
    }

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

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        producer.stopSimulation();
        return ResponseEntity.ok(Map.of(
                "stopped", true,
                "message", "Kafka simülasyonu durduruldu."
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        var status = producer.getStatus();
        long oracleRecordedCount = 0;
        try {
            oracleRecordedCount = indexingStateRepository.count();
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
                "running", status.running(),
                "publishedCount", status.publishedCount(),
                "targetLimit", status.targetLimit(),
                "delayMs", status.delayMs(),
                "topic", status.topic(),
                "bootstrapServers", status.bootstrapServers(),
                "status", status.status(),
                "lastEventSummary", status.lastEventSummary() != null ? status.lastEventSummary() : "",
                "oracleRecordedCount", oracleRecordedCount
        ));
    }
}
