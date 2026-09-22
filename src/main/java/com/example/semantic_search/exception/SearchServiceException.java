package com.example.semantic_search.exception;

/**
 * Arama ve indeksleme servislerinde genel iş mantığı veya çalışma zamanı
 * hatalarını temsil eden temel çalışma zamanı istisnası (base runtime exception).
 *
 * <p>Tüm arama altyapı özel istisnaları (örneğin {@link DocumentNotFoundException},
 * {@link OpenSearchUnavailableException}, {@link EmbeddingUnavailableException})
 * bu sınıftan türer. Bu sayede servis katmanındaki tüm arama kökenli hatalar
 * tek bir çatı altında yakalanıp işlenebilir.</p>
 */
public class SearchServiceException extends RuntimeException {

    /**
     * Belirtilen hata mesajıyla yeni bir SearchServiceException oluşturur.
     *
     * @param message Hatanın detaylı açıklaması
     */
    public SearchServiceException(String message) {
        super(message);
    }

    /**
     * Belirtilen hata mesajı ve kök neden (cause) ile yeni bir SearchServiceException oluşturur.
     *
     * @param message Hatanın detaylı açıklaması
     * @param cause Hatanın altında yatan asıl istisna (throwable)
     */
    public SearchServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
