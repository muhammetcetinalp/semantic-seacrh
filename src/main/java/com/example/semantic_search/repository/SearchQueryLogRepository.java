package com.example.semantic_search.repository;

import com.example.semantic_search.model.SearchQueryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * {@link SearchQueryLog} arama denetim ve analiz kayıtları için Spring Data JPA veri erişim katmanı (Repository).
 *
 * <p>Geçmiş arama loglarının incelenmesi, başarı oranlarının hesaplanması ve hata tespiti
 * için gerekli veritabanı sorgularını sunar.</p>
 */
@Repository
public interface SearchQueryLogRepository extends JpaRepository<SearchQueryLog, Long> {

    /**
     * En son çalıştırılan 50 arama logunu tarihe göre azalan sırada getirir.
     *
     * @return Son 50 arama kaydı listesi
     */
    List<SearchQueryLog> findTop50ByOrderByCreatedAtDesc();

    /**
     * Belirli bir metni içeren geçmiş sorguları arar (büyük/küçük harf duyarsız).
     *
     * @param query Aranacak kelime
     * @return Eşleşen arama logları
     */
    List<SearchQueryLog> findByQueryContainingIgnoreCase(String query);

    /**
     * Belirli bir zaman damgasından sonra yapılmış aramaları listeler.
     *
     * @param since Başlangıç zamanı
     * @return Belirtilen tarihten sonraki arama kayıtları
     */
    @Query("SELECT s FROM SearchQueryLog s WHERE s.createdAt >= :since ORDER BY s.createdAt DESC")
    List<SearchQueryLog> findSince(Instant since);

    /**
     * Belirli bir duruma (örn: SUCCESS veya ERROR) sahip toplam kayıt sayısını döndürür.
     *
     * @param status Durum değeri
     * @return Toplam adet
     */
    long countByStatus(String status);
}
