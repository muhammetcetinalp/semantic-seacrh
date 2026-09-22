package com.example.semantic_search.kafka;

/**
 * Geçersiz arama olayı istisnası.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.exception.InvalidSearchEventException} paketine taşınmıştır.
 *             Geriye dönük uyumluluk amacıyla korunmaktadır.
 */
@Deprecated
public class InvalidSearchEventException extends com.example.semantic_search.exception.InvalidSearchEventException {

    /**
     * Hata mesajı ile oluşturur.
     *
     * @param message Hata mesajı
     */
    public InvalidSearchEventException(String message) {
        super(message);
    }

    /**
     * Hata mesajı ve kök neden ile oluşturur.
     *
     * @param message Hata mesajı
     * @param cause Kök neden
     */
    public InvalidSearchEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
