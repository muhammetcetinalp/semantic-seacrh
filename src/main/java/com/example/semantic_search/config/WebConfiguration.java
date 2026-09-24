package com.example.semantic_search.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Web MVC katmanı için CORS (Cross-Origin Resource Sharing) kurallarını belirleyen yapılandırma.
 *
 * <p>Frontend uygulamasının (Vite React - localhost:5173) REST API uç noktalarına
 * sorunsuz erişebilmesi için gerekli köken ve başlık izinlerini tanımlar.</p>
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    /** İzin verilen web kökenleri listesi. */
    @Value("${search.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String[] allowedOrigins;

    /**
     * /api/** altındaki tüm uç noktalara yönelik CORS izinlerini kaydeder.
     *
     * @param registry Spring CORS kayıt yöneticisi
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    /**
     * Model istemcileri (Embedding & Reranker) için standart RestClient.Builder bileşeni.
     */
    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
