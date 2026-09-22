package com.example.semantic_search.indexing;

import com.example.semantic_search.client.opensearch.OpenSearchAdapter;
import com.example.semantic_search.model.IndexingState;
import com.example.semantic_search.model.IndexingStatus;
import com.example.semantic_search.repository.IndexingStateRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real PostgreSQL database with Flyway migrations and Hibernate validation.
 * Enable with ./mvnw -Ppostgres-it verify. Test data is rolled back after each test.
 */
@SpringBootTest
@ActiveProfiles("postgres-it")
@Transactional
class PostgresPersistenceIT {

    @MockitoBean
    private OpenSearchAdapter openSearchAdapter;

    @Autowired
    private IndexingStateRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistGeneratedIdUnicodeTextAndTimestampAndSupportUpdateAndDelete() {
        IndexingState state = newState();
        Instant indexedAt = Instant.parse("2026-09-18T12:34:56.123456Z");
        String errorMessage = "İndeksleme hatası: ğüşıöç".repeat(300);
        state.setLastIndexedAt(indexedAt);
        state.setErrorMessage(errorMessage);
        state.setSearchTextHash("a".repeat(64));
        state.setLastEventId("postgres-event-v7");
        state.setLastEventVersion(7L);
        state.setDocumentSource("{\"id\":\"postgres-doc\",\"region\":\"İstanbul\",\"embedding\":[0.1,0.2]}");

        repository.saveAndFlush(state);
        assertThat(state.getId()).isPositive();
        entityManager.clear();

        IndexingState loaded = repository.findByDocumentIdAndIndexNameForUpdate(
                state.getDocumentId(), state.getIndexName()).orElseThrow();
        assertThat(loaded.getLastIndexedAt()).isEqualTo(indexedAt);
        assertThat(loaded.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(loaded.getSearchTextHash()).isEqualTo(state.getSearchTextHash());
        assertThat(loaded.getLastEventId()).isEqualTo("postgres-event-v7");
        assertThat(loaded.getLastEventVersion()).isEqualTo(7L);
        assertThat(loaded.getDocumentSource()).isEqualTo(state.getDocumentSource());
        assertThat(loaded.getStatus()).isEqualTo(IndexingStatus.PENDING);
        assertThat(loaded.getCreatedAt()).isNotNull();

        loaded.setStatus(IndexingStatus.INDEXED);
        repository.saveAndFlush(loaded);
        entityManager.clear();
        assertThat(repository.findById(state.getId()).orElseThrow().getStatus())
                .isEqualTo(IndexingStatus.INDEXED);

        repository.deleteById(state.getId());
        repository.flush();
        entityManager.clear();
        assertThat(repository.findById(state.getId())).isEmpty();
    }

    @Test
    void shouldRejectDuplicateDocumentInTheSameIndex() {
        IndexingState first = newState();
        repository.saveAndFlush(first);

        IndexingState duplicate = new IndexingState();
        duplicate.setDocumentId(first.getDocumentId());
        duplicate.setIndexName(first.getIndexName());

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectInvalidOpenSearchDocumentJson() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO indexing_state (document_id, index_name, document_source)
                        VALUES (?, ?, ?)
                        """, UUID.randomUUID().toString(), "postgres-json-verification", "not-json"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private IndexingState newState() {
        IndexingState state = new IndexingState();
        state.setDocumentId(UUID.randomUUID().toString());
        state.setIndexName("postgres-persistence-verification");
        return state;
    }
}
