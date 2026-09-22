package com.example.semantic_search.client.embedding;

import com.example.semantic_search.config.EmbeddingProperties;
import com.example.semantic_search.exception.EmbeddingUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Harici bir HTTP REST servisine (ör. OpenAI uyumlu embedding API'leri) delegasyon yaparak
 * metin gömme vektörleri üreten sağlayıcı sınıfı.
 *
 * <p>{@code search.embedding.provider=rest} konfigürasyonu aktif olduğunda devreye girer.</p>
 */
@Component
@ConditionalOnProperty(name = "search.embedding.provider", havingValue = "rest")
public class RestEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(RestEmbeddingProvider.class);

    private final RestClient restClient;
    private final EmbeddingProperties properties;

    /**
     * REST istemcisi ve yapılandırma özelliklerini enjekte eden yapıcı metot.
     *
     * @param restClientBuilder Spring WebClient / RestClient yapıcısı
     * @param properties Uç nokta ve model yapılandırması
     */
    public RestEmbeddingProvider(RestClient.Builder restClientBuilder, EmbeddingProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getEndpoint())
                .build();
        this.properties = properties;
        log.info("REST embedding sağlayıcısı aktif — endpoint={}, model={}",
                properties.getEndpoint(), properties.getModel());
    }

    /**
     * Verilen metni REST uç noktasına gönderir, dönen yanıtı ayrıştırır ve L2 normalize edilmiş float dizisi döner.
     *
     * @param text Vektöre dönüştürülecek metin
     * @return L2 normalize float dizisi
     * @throws EmbeddingUnavailableException Servis erişilemez olduğunda veya boş döndüğünde
     */
    @Override
    @SuppressWarnings("unchecked")
    public float[] generateEmbedding(String text) {
        try {
            Map<String, Object> request = Map.of(
                    "input", text,
                    "model", properties.getModel()
            );

            Map<String, Object> response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new EmbeddingUnavailableException("Embedding servisinden boş yanıt alındı");
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) {
                throw new EmbeddingUnavailableException("Yanıt içerisinde embedding verisi bulunamadı");
            }

            List<Number> embeddingList = (List<Number>) data.getFirst().get("embedding");
            float[] embedding = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                embedding[i] = embeddingList.get(i).floatValue();
            }
            return EmbeddingProvider.normalizeL2(embedding);

        } catch (EmbeddingUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Embedding üretimi başarısız: {}", e.getMessage());
            throw new EmbeddingUnavailableException("Embedding servisi kullanılamıyor: " + e.getMessage(), e);
        }
    }

    /**
     * Vektör boyutunu döner.
     *
     * @return Boyut adedi
     */
    @Override
    public int getDimensions() {
        return properties.getDimensions();
    }

    /**
     * Uç noktaya basit bir GET isteği yaparak servisin erişilebilirliğini kontrol eder.
     *
     * @return Servis ayaktaysa true, değilse false
     */
    @Override
    public boolean isAvailable() {
        try {
            restClient.get().retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
