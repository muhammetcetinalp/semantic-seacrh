package com.example.semantic_search.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** Example wire contract. A mapper can adapt the environment's actual event format. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchIndexingEvent(
        @NotBlank @Size(max = 128) String eventId,
        @NotBlank String eventType,
        @NotBlank @Size(max = 255) String documentId,
        @NotNull @PositiveOrZero Long version,
        JsonNode data) { }
