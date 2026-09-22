package com.example.semantic_search.exception;

/**
 * Hata yanıtı transfer nesnesi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.dto.ErrorResponse} paketine taşınmıştır.
 *             Geriye dönük uyumluluk amacıyla korunmaktadır.
 */
@Deprecated
public class ErrorResponse extends com.example.semantic_search.dto.ErrorResponse {

    /**
     * Varsayılan yapıcı metot.
     */
    public ErrorResponse() {
        super();
    }

    /**
     * Durum kodu, hata başlığı ve mesajı ile hata yanıtı oluşturur.
     *
     * @param status HTTP durum kodu
     * @param error Hata tipi/başlığı
     * @param message Detaylı hata mesajı
     */
    public ErrorResponse(int status, String error, String message) {
        super(status, error, message);
    }
}
