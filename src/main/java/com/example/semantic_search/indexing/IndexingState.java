package com.example.semantic_search.indexing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persistent state for tracking document indexing status in Oracle Database.
 * This is not domain data — it's the Search Service's own operational state.
 */
@Entity
@Table(name = "indexing_state")
public class IndexingState {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "indexing_state_id")
    @SequenceGenerator(name = "indexing_state_id", sequenceName = "indexing_state_seq", allocationSize = 1)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "index_name", nullable = false)
    private String indexName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IndexingStatus status = IndexingStatus.PENDING;

    @Column(name = "search_text_hash", length = 64)
    private String searchTextHash;

    @Column(name = "last_event_id", length = 128)
    private String lastEventId;

    @Column(name = "last_event_version")
    private Long lastEventVersion;

    /** Exact JSON source last sent to OpenSearch; null for deleted documents. */
    @Lob
    @Column(name = "document_source")
    private String documentSource;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // --- Getters / Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

    public IndexingStatus getStatus() { return status; }
    public void setStatus(IndexingStatus status) { this.status = status; }

    public String getSearchTextHash() { return searchTextHash; }
    public void setSearchTextHash(String searchTextHash) { this.searchTextHash = searchTextHash; }

    public String getLastEventId() { return lastEventId; }
    public void setLastEventId(String lastEventId) { this.lastEventId = lastEventId; }

    public Long getLastEventVersion() { return lastEventVersion; }
    public void setLastEventVersion(Long lastEventVersion) { this.lastEventVersion = lastEventVersion; }

    public String getDocumentSource() { return documentSource; }
    public void setDocumentSource(String documentSource) { this.documentSource = documentSource; }

    public Instant getLastIndexedAt() { return lastIndexedAt; }
    public void setLastIndexedAt(Instant lastIndexedAt) { this.lastIndexedAt = lastIndexedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
