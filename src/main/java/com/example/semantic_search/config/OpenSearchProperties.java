package com.example.semantic_search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenSearch kümesine bağlantı parametrelerini yöneten yapılandırma sınıfı.
 *
 * <p>application.yml dosyasındaki {@code search.opensearch} öneki altındaki özellikleri bağlar.
 * Sunucu adresi, port, protokol, kimlik doğrulama ve zaman aşımı sürelerini yönetir.</p>
 */
@ConfigurationProperties(prefix = "search.opensearch")
public class OpenSearchProperties {

    /** OpenSearch sunucu ana makine adı veya IP adresi. */
    private String host = "localhost";

    /** OpenSearch HTTP REST API port numarası. */
    private int port = 9200;

    /** İletişim protokolü (http veya https). */
    private String scheme = "http";

    /** Varsa temel kimlik doğrulama kullanıcı adı. */
    private String username;

    /** Varsa temel kimlik doğrulama parolası. */
    private String password;

    /** Bağlantı kurma zaman aşımı süresi (milisaniye cinsinden). */
    private int connectTimeout = 5000;

    /** Veri alışverişi soket zaman aşımı süresi (milisaniye cinsinden). */
    private int socketTimeout = 60000;

    /**
     * Sunucu ana makine adını döndürür.
     *
     * @return Host adı
     */
    public String getHost() {
        return host;
    }

    /**
     * Sunucu ana makine adını günceller.
     *
     * @param host Host adı
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Port numarasını döndürür.
     *
     * @return Port numarası
     */
    public int getPort() {
        return port;
    }

    /**
     * Port numarasını ayarlar.
     *
     * @param port Port numarası
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Protokol şemasını (http/https) döndürür.
     *
     * @return Protokol şeması
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * Protokol şemasını günceller.
     *
     * @param scheme Protokol şeması
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    /**
     * Kimlik doğrulama kullanıcı adını döndürür.
     *
     * @return Kullanıcı adı veya null
     */
    public String getUsername() {
        return username;
    }

    /**
     * Kimlik doğrulama kullanıcı adını ayarlar.
     *
     * @param username Kullanıcı adı
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Kimlik doğrulama parolasını döndürür.
     *
     * @return Parola veya null
     */
    public String getPassword() {
        return password;
    }

    /**
     * Kimlik doğrulama parolasını ayarlar.
     *
     * @param password Parola
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Bağlantı zaman aşımı süresini döndürür.
     *
     * @return Bağlantı zaman aşımı (ms)
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Bağlantı zaman aşımı süresini ayarlar.
     *
     * @param connectTimeout Bağlantı zaman aşımı (ms)
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Soket veri alışverişi zaman aşımı süresini döndürür.
     *
     * @return Soket zaman aşımı (ms)
     */
    public int getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * Soket veri alışverişi zaman aşımı süresini ayarlar.
     *
     * @param socketTimeout Soket zaman aşımı (ms)
     */
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }
}
