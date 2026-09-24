package com.example.semantic_search.repository;

import com.example.semantic_search.model.SearchQueryLog;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Arama sorgu loglarını bellek içinde (in-memory) saklayan repository bileşeni.
 *
 * <p>İlişkisel veritabanı olmaksızın arama metriklerini ve geçmiş kayıtlarını
 * thread-safe bir yapıda tutar.</p>
 */
@Repository
public class SearchQueryLogRepository {

    private final Map<Long, SearchQueryLog> storage = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public SearchQueryLog save(SearchQueryLog log) {
        if (log == null) {
            return null;
        }
        if (log.getId() == null) {
            log.setId(idSeq.getAndIncrement());
        }
        if (log.getCreatedAt() == null) {
            log.setCreatedAt(Instant.now());
        }
        storage.put(log.getId(), log);
        return log;
    }

    public List<SearchQueryLog> findTop50ByOrderByCreatedAtDesc() {
        return storage.values().stream()
                .sorted(Comparator.comparing(SearchQueryLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(50)
                .toList();
    }

    public List<SearchQueryLog> findByQueryContainingIgnoreCase(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String lower = query.toLowerCase(Locale.ROOT);
        return storage.values().stream()
                .filter(l -> l.getQuery() != null && l.getQuery().toLowerCase(Locale.ROOT).contains(lower))
                .toList();
    }

    public List<SearchQueryLog> findSince(Instant since) {
        if (since == null) {
            return Collections.emptyList();
        }
        return storage.values().stream()
                .filter(l -> l.getCreatedAt() != null && !l.getCreatedAt().isBefore(since))
                .sorted(Comparator.comparing(SearchQueryLog::getCreatedAt, Comparator.reverseOrder()))
                .toList();
    }

    public long countByStatus(String status) {
        return storage.values().stream()
                .filter(l -> Objects.equals(l.getStatus(), status))
                .count();
    }

    public long count() {
        return storage.size();
    }

    public Optional<SearchQueryLog> findById(Long id) {
        return id != null ? Optional.ofNullable(storage.get(id)) : Optional.empty();
    }

    public List<SearchQueryLog> findAll() {
        return new ArrayList<>(storage.values());
    }

    public void deleteAll() {
        storage.clear();
    }
}
