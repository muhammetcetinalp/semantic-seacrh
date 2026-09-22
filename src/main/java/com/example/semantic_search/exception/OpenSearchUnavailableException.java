package com.example.semantic_search.exception;

/**
 * OpenSearch kümesine veya düğümüne erişilemediğinde, bağlantı koptuğunda
 * ya da istek zaman aşımına uğradığında fırlatılan özel istisna sınıfı.
 *
 * <p>REST API katmanında HTTP 503 (Service Unavailable) durum kodu ile istemciye bildirilir.</p>
 */
public class OpenSearchUnavailableException extends SearchServiceException {

    /**
     * Belirtilen hata mesajıyla yeni bir OpenSearchUnavailableException oluşturur.
     *
     * @param message Hata detay açıklaması
     */
    public OpenSearchUnavailableException(String message) {
        super(message);
    }

    /**
     * Belirtilen hata mesajı ve kök neden ile yeni bir OpenSearchUnavailableException oluşturur.
     *
     * @param message Hata detay açıklaması
     * @param cause Hatanın altında yatan asıl istisna
     */
    public OpenSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
