package com.example.semantic_search.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * Kafka üzerinden asenkron veri akışında tüketilen indeksleme olay sözleşmesi (Record).
 *
 * <p>Harici sistemler bu formatta olay üreterek dokümanların otomatik olarak OpenSearch
 * indeksinde oluşturulmasını, güncellenmesini veya silinmesini tetikler.</p>
 *
 * @param eventId Olayın benzersiz UUID kimliği (idempotency için)
 * @param eventType Olay türü (örn: CREATED, UPDATED, DELETED)
 * @param documentId İlgili dokümanın iş kimliği (ID)
 * @param version Sırasız olayların eski veriyi ezmesini önleyen artan sürüm numarası
 * @param data Dokümanın ham JSON içeriği
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchIndexingEvent(
        @NotBlank @Size(max = 128) String eventId,
        @NotBlank String eventType,
        @NotBlank @Size(max = 255) String documentId,
        @NotNull @PositiveOrZero Long version,
        JsonNode data) { }
