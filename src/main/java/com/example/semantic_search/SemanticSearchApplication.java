package com.example.semantic_search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Semantik Arama ve İndeksleme Spring Boot Ana Uygulama Sınıfı.
 *
 * <p>Bu uygulama hibrit arama (BM25 + BGE-M3 / TEI k-NN kosinüs vektörleri),
 * saf Java ColBERT (Contextualized Late Interaction - MaxSim), Qdrant çoklu-vektör
 * veritabanı entegrasyonu, Cross-Encoder yeniden sıralama (reranking) ve
 * Kafka tabanlı olay odaklı (event-driven) indeksleme yeteneklerini barındırır.</p>
 */
@SpringBootApplication
@EnableAsync
public class SemanticSearchApplication {

    /**
     * Spring Boot uygulamasını başlatan ana giriş noktası.
     *
     * @param args Komut satırı argümanları
     */
    public static void main(String[] args) {
        SpringApplication.run(SemanticSearchApplication.class, args);
    }
}
