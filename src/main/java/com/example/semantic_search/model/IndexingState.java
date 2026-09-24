package com.example.semantic_search.model;

import java.time.Instant;

/**
 * Dokümanların indeksleme durumunu takip eden operasyonel model.
 *
 * <p>Dokümanın OpenSearch'e başarıyla yazılıp yazılmadığını, en son hangi olay versiyonunun işlendiğini,
 * hata durumlarını ve tekrar deneme (retry) sayılarını takip ederek veri tutarlılığını garanti eder.</p>
 */
public class IndexingState {

    /** Otomatik artan kayıt kimliği. */
    private Long id;

    /** İndekslenen dokümanın harici iş kimliği (ID). */
    private String documentId;

    /** Dokümanın yazıldığı OpenSearch indeksinin adı. */
    private String indexName;

    /** Dokümanın anlık indeksleme durumu (PENDING, INDEXED, FAILED, DELETED). */
    private IndexingStatus status = IndexingStatus.PENDING;

    /** Metin içeriğindeki değişiklikleri tespit etmek için kullanılan SHA-256 özeti. */
    private String searchTextHash;

    /** Dokümanı güncelleyen son Kafka olayının kimliği. */
    private String lastEventId;

    /** Dokümana ait son işlenen olay sürüm numarası (sırasız olayları engellemek için). */
    private Long lastEventVersion;

    /** OpenSearch'e gönderilen orijinal JSON doküman kaynağı. */
    private String documentSource;

    /** Dokümanın OpenSearch üzerinde en son başarılı indekslendiği zaman. */
    private Instant lastIndexedAt;

    /** Başarısızlık durumunda kaydedilen hata mesajı. */
    private String errorMessage;

    /** Hata sonrası yapılan yeniden deneme sayısı. */
    private int retryCount = 0;

    /** Kaydın ilk oluşturulma zamanı. */
    private Instant createdAt = Instant.now();

    /** Kaydın son güncellenme zamanı. */
    private Instant updatedAt = Instant.now();

    public IndexingState() {
    }

    public IndexingState(String documentId, String indexName) {
        this.documentId = documentId;
        this.indexName = indexName;
    }

    // --- Getters / Setters ---

    /** @return Kayıt ID */
    public Long getId() { return id; }
    /** @param id Kayıt ID */
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
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** @return Son güncellenme zamanı */
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
