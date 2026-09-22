package com.example.semantic_search.repository;

import com.example.semantic_search.model.IndexingState;
import com.example.semantic_search.model.IndexingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link IndexingState} varlığı için Spring Data JPA veri erişim katmanı (Repository).
 *
 * <p>İndeksleme durumlarını, tekrar deneme döngülerini ve eşzamanlı Kafka mesajlarının
 * birbirini ezmesini önlemek amacıyla karamsar kilitleme (pessimistic locking) sorgularını sağlar.</p>
 */
@Repository
public interface IndexingStateRepository extends JpaRepository<IndexingState, Long> {

    /**
     * Doküman ID ve indeks adına göre mevcut indeksleme durumunu sorgular.
     *
     * @param documentId Doküman kimliği
     * @param indexName İndeks adı
     * @return Varsa indeksleme durumu nesnesi
     */
    Optional<IndexingState> findByDocumentIdAndIndexName(String documentId, String indexName);

    /**
     * Eşzamanlı mesaj tüketiminde veri yarışını (race condition) önlemek amacıyla
     * veritabanı seviyesinde FOR UPDATE kilidi koyarak kaydı getirir.
     *
     * @param documentId Doküman kimliği
     * @param indexName İndeks adı
     * @return Kilitlenmiş indeksleme durumu nesnesi
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IndexingState s where s.documentId = :documentId and s.indexName = :indexName")
    Optional<IndexingState> findByDocumentIdAndIndexNameForUpdate(
            @Param("documentId") String documentId, @Param("indexName") String indexName);

    /**
     * Belirli bir duruma (örn: PENDING veya FAILED) sahip kayıtları listeler.
     *
     * @param status İstenen indeksleme durumu
     * @return Eşleşen kayıtlar listesi
     */
    List<IndexingState> findByStatus(IndexingStatus status);

    /**
     * Doküman ve indeks eşleşmesine göre indeksleme takip kaydını siler.
     *
     * @param documentId Doküman kimliği
     * @param indexName İndeks adı
     */
    void deleteByDocumentIdAndIndexName(String documentId, String indexName);
}
