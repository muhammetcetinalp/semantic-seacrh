package com.example.semantic_search.dto;

import java.time.Instant;

/**
 * REST API çağrılarında meydana gelen hata durumlarında istemciye dönülen standart hata yanıt DTO'su.
 *
 * <p>HTTP durum kodunu, hata kategorisini, açıklayıcı hata iletisini ve hatanın gerçekleştiği
 * zaman damgasını barındırır.</p>
 */
public class ErrorResponse {

    /** HTTP durum kodu (örn: 400, 404, 500, 503). */
    private int status;

    /** Kısa hata tanımı veya HTTP durum başlığı (örn: Bad Request, Not Found). */
    private String error;

    /** Kullanıcıya ve geliştiriciye yardımcı olacak detaylı hata iletisi. */
    private String message;

    /** Hatanın meydana geldiği UTC anı. */
    private Instant timestamp;

    /**
     * Varsayılan yapıcı metot (Zaman damgası şu anki an olarak başlatılır).
     */
    public ErrorResponse() {
        this.timestamp = Instant.now();
    }

    /**
     * Parametreli yapıcı metot.
     *
     * @param status HTTP durum kodu
     * @param error Hata başlığı
     * @param message Açıklayıcı hata mesajı
     */
    public ErrorResponse(int status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = Instant.now();
    }

    /**
     * HTTP durum kodunu döndürür.
     *
     * @return HTTP status kodu
     */
    public int getStatus() {
        return status;
    }

    /**
     * HTTP durum kodunu ayarlar.
     *
     * @param status HTTP status kodu
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * Hata başlığını döndürür.
     *
     * @return Hata başlığı
     */
    public String getError() {
        return error;
    }

    /**
     * Hata başlığını ayarlar.
     *
     * @param error Hata başlığı
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Detaylı hata mesajını döndürür.
     *
     * @return Hata mesajı
     */
    public String getMessage() {
        return message;
    }

    /**
     * Detaylı hata mesajını ayarlar.
     *
     * @param message Hata mesajı
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Hatanın oluştuğu zaman damgasını döndürür.
     *
     * @return Zaman damgası
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Hatanın zaman damgasını ayarlar.
     *
     * @param timestamp Zaman damgası
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
