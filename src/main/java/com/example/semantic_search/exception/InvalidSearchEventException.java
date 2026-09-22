package com.example.semantic_search.exception;

/**
 * Kafka veya diğer olay kuyruklarından gelen arama/indeksleme olay mesajlarının
 * geçersiz, şemaya uymayan ya da bozuk (corrupted payload) olması durumunda fırlatılan istisna.
 *
 * <p>Bu hata kalıcı bir veri biçimi hatası olduğundan ötürü, tekrar denemek (retry) yerine
 * mesaj doğrudan Dead-Letter Queue (DLQ / DLT) kuyruğuna yönlendirilir.</p>
 */
public class InvalidSearchEventException extends RuntimeException {

    /**
     * Belirtilen hata mesajıyla yeni bir InvalidSearchEventException oluşturur.
     *
     * @param message Hata açıklaması
     */
    public InvalidSearchEventException(String message) {
        super(message);
    }

    /**
     * Belirtilen hata mesajı ve kök neden ile yeni bir InvalidSearchEventException oluşturur.
     *
     * @param message Hata açıklaması
     * @param cause Hatanın altında yatan asıl istisna
     */
    public InvalidSearchEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
