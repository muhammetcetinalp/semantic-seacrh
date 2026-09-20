package com.example.semantic_search.search;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SearchQueryLogRepository extends JpaRepository<SearchQueryLog, Long> {

    List<SearchQueryLog> findTop50ByOrderByCreatedAtDesc();

    List<SearchQueryLog> findByQueryContainingIgnoreCase(String query);

    @Query("SELECT s FROM SearchQueryLog s WHERE s.createdAt >= :since ORDER BY s.createdAt DESC")
    List<SearchQueryLog> findSince(Instant since);

    long countByStatus(String status);
}
