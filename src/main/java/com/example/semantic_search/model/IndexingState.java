package com.example.semantic_search.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * İlişkisel veritabanında (PostgreSQL) dokümanların indeksleme durumunu takip eden JPA varlığı (Entity).
 *
 * <p>Bu varlık iş verisinin kendisi değildir; Arama Servisi'nin kendi operasyonel durumudur.
 * Dokümanın OpenSearch'e başarıyla yazılıp yazılmadığını, en son hangi olay versiyonunun işlendiğini,
 * hata durumlarını ve tekrar deneme (retry) sayılarını takip ederek veri tutarlılığını garanti eder.</p>
 */
@Entity
@Table(name = "indexing_state")
public class IndexingState {

    /** Otomatik artan birincil anahtar (Primary Key). */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "indexing_state_id")
    @SequenceGenerator(name = "indexing_state_id", sequenceName = "indexing_state_seq", allocationSize = 1)
    private Long id;

    /** İndekslenen dokümanın harici iş kimliği (ID). */
    @Column(name = "document_id", nullable = false)
    private String documentId;

    /** Dokümanın yazıldığı OpenSearch indeksinin adı. */
    @Column(name = "index_name", nullable = false)
    private String indexName;

    /** Dokümanın anlık indeksleme durumu (PENDING, INDEXED, FAILED, DELETED). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IndexingStatus status = IndexingStatus.PENDING;

    /** Metin içeriğindeki değişiklikleri tespit etmek için kullanılan SHA-256 özeti. */
    @Column(name = "search_text_hash", length = 64)
    private String searchTextHash;

    /** Dokümanı güncelleyen son Kafka olayının kimliği. */
    @Column(name = "last_event_id", length = 128)
    private String lastEventId;

    /** Dokümana ait son işlenen olay sürüm numarası (sırasız olayları engellemek için). */
    @Column(name = "last_event_version")
    private Long lastEventVersion;

    /** OpenSearch'e gönderilen orijinal JSON doküman kaynağı (TEXT tipinde). */
    @Column(name = "document_source", columnDefinition = "TEXT")
    private String documentSource;

    /** Dokümanın OpenSearch üzerinde en son başarılı indekslendiği zaman. */
    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

    /** Başarısızlık durumunda kaydedilen hata mesajı. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Hata sonrası yapılan yeniden deneme sayısı. */
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /** Kaydın ilk oluşturulma zamanı. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Kaydın son güncellenme zamanı. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Varlık kaydedilmeden önce oluşturulma ve güncellenme tarihlerini ayarlar.
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Varlık güncellenmeden önce son güncellenme tarihini yeniler.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // --- Getters / Setters ---

    /** @return Veritabanı ID */
    public Long getId() { return id; }
    /** @param id Veritabanı ID */
    public void setId(Long id) { this.id = id; }

    /** @return Doküman ID */
    public String getDocumentId() { return documentId; }
    /** @param documentId Doküman ID */
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    /** @return İndeks adı */
    public String getIndexName() { return indexName; }
    /** @param indexName İndeks adı */
    public void setIndexName(String indexName) { this.indexName = indexName; }

    /** @return İndeksleme durumu */
    public IndexingStatus getStatus() { return status; }
    /** @param status İndeksleme durumu */
    public void setStatus(IndexingStatus status) { this.status = status; }

    /** @return Arama metninin hash değeri */
    public String getSearchTextHash() { return searchTextHash; }
    /** @param searchTextHash Metin hash değeri */
    public void setSearchTextHash(String searchTextHash) { this.searchTextHash = searchTextHash; }

    /** @return Son Kafka olay ID */
    public String getLastEventId() { return lastEventId; }
    /** @param lastEventId Son Kafka olay ID */
    public void setLastEventId(String lastEventId) { this.lastEventId = lastEventId; }

    /** @return Son olay versiyonu */
    public Long getLastEventVersion() { return lastEventVersion; }
    /** @param lastEventVersion Son olay versiyonu */
    public void setLastEventVersion(Long lastEventVersion) { this.lastEventVersion = lastEventVersion; }

    /** @return Doküman JSON kaynağı */
    public String getDocumentSource() { return documentSource; }
    /** @param documentSource Doküman JSON kaynağı */
    public void setDocumentSource(String documentSource) { this.documentSource = documentSource; }

    /** @return Son indekslenme anı */
    public Instant getLastIndexedAt() { return lastIndexedAt; }
    /** @param lastIndexedAt Son indekslenme anı */
    public void setLastIndexedAt(Instant lastIndexedAt) { this.lastIndexedAt = lastIndexedAt; }

    /** @return Varsa hata iletisi */
    public String getErrorMessage() { return errorMessage; }
    /** @param errorMessage Hata iletisi */
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    /** @return Tekrar deneme sayısı */
    public int getRetryCount() { return retryCount; }
    /** @param retryCount Tekrar deneme sayısı */
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    /** @return Oluşturulma zamanı */
    public Instant getCreatedAt() { return createdAt; }

    /** @return Son güncellenme zamanı */
    public Instant getUpdatedAt() { return updatedAt; }
}
