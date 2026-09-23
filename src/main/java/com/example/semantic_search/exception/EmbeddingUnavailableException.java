package com.example.semantic_search.exception;

/**
 * Metin gömme (embedding) servis sağlayıcısına (TEI / HuggingFace / Harici Model API vb.)
 * erişilemediğinde, zaman aşımı (timeout) meydana geldiğinde veya servis hata verdiğinde fırlatılan istisna.
 *
 * <p>REST API katmanında HTTP 503 (Service Unavailable) durum koduyla karşılanır.</p>
 */
public class EmbeddingUnavailableException extends SearchServiceException {

    /**
     * Belirtilen hata mesajıyla yeni bir EmbeddingUnavailableException oluşturur.
     *
     * @param message Hata detay açıklaması
     */
    public EmbeddingUnavailableException(String message) {
        super(message);
    }

    /**
     * Belirtilen hata mesajı ve kök neden ile yeni bir EmbeddingUnavailableException oluşturur.
     *
     * @param message Hata detay açıklaması
     * @param cause Hatanın altında yatan asıl istisna
     */
    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
