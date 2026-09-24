package com.example.semantic_search.repository;

import com.example.semantic_search.model.IndexingState;
import com.example.semantic_search.model.IndexingStatus;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Doküman indeksleme durumlarını bellek içinde (in-memory) saklayan repository bileşeni.
 *
 * <p>Harici bir ilişkisel veritabanı (PostgreSQL) ihtiyacını ortadan kaldırarak
 * thread-safe eşzamanlı harita (ConcurrentHashMap) üzerinde idempotency ve sürüm takibi sağlar.</p>
 */
@Repository
public class IndexingStateRepository {

    private final Map<String, IndexingState> storage = new ConcurrentHashMap<>();
    private final Map<Long, IndexingState> idIndex = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    private String compositeKey(String documentId, String indexName) {
        return (indexName != null ? indexName : "") + "::" + (documentId != null ? documentId : "");
    }

    public synchronized IndexingState save(IndexingState state) {
        if (state == null) {
            return null;
        }
        if (state.getId() == null) {
            state.setId(idSeq.getAndIncrement());
        }
        if (state.getCreatedAt() == null) {
            state.setCreatedAt(Instant.now());
        }
        state.setUpdatedAt(Instant.now());

        String key = compositeKey(state.getDocumentId(), state.getIndexName());
        storage.put(key, state);
        idIndex.put(state.getId(), state);
        return state;
    }

    public IndexingState saveAndFlush(IndexingState state) {
        return save(state);
    }

    public Optional<IndexingState> findByDocumentIdAndIndexName(String documentId, String indexName) {
        if (documentId == null || indexName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(compositeKey(documentId, indexName)));
    }

    public Optional<IndexingState> findByDocumentIdAndIndexNameForUpdate(String documentId, String indexName) {
        return findByDocumentIdAndIndexName(documentId, indexName);
    }

    public List<IndexingState> findByStatus(IndexingStatus status) {
        return storage.values().stream()
                .filter(s -> s.getStatus() == status)
                .toList();
    }

    public void deleteByDocumentIdAndIndexName(String documentId, String indexName) {
        if (documentId == null || indexName == null) {
            return;
        }
        IndexingState removed = storage.remove(compositeKey(documentId, indexName));
        if (removed != null && removed.getId() != null) {
            idIndex.remove(removed.getId());
        }
    }

    public void deleteById(Long id) {
        if (id == null) return;
        IndexingState state = idIndex.remove(id);
        if (state != null) {
            storage.remove(compositeKey(state.getDocumentId(), state.getIndexName()));
        }
    }

    public Optional<IndexingState> findById(Long id) {
        return id != null ? Optional.ofNullable(idIndex.get(id)) : Optional.empty();
    }

    public List<IndexingState> findAll() {
        return new ArrayList<>(storage.values());
    }

    public long count() {
        return storage.size();
    }

    public void deleteAll() {
        storage.clear();
        idIndex.clear();
    }

    public void flush() {
        // In-memory no-op
    }
}
