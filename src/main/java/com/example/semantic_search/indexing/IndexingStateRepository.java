package com.example.semantic_search.indexing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndexingStateRepository extends JpaRepository<IndexingState, Long> {

    Optional<IndexingState> findByDocumentIdAndIndexName(String documentId, String indexName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IndexingState s where s.documentId = :documentId and s.indexName = :indexName")
    Optional<IndexingState> findByDocumentIdAndIndexNameForUpdate(
            @Param("documentId") String documentId, @Param("indexName") String indexName);

    List<IndexingState> findByStatus(IndexingStatus status);

    void deleteByDocumentIdAndIndexName(String documentId, String indexName);
}
