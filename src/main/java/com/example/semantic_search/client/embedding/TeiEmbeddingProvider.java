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
 * Hugging Face Text Embeddings Inference (TEI) konteyneri ile entegre çalışan yüksek performanslı vektör sağlayıcısı.
 *
 * <p>TEI servisi doğrudan {@code POST /embed} uç noktası üzerinden metin kabul eder ve
 * doğrudan {@code float[][]} formatında ham gömme dizisi döndürür.</p>
 *
 * <p>{@code search.embedding.provider=tei} yapılandırması seçildiğinde aktifleşir.</p>
 */
@Component
@ConditionalOnProperty(name = "search.embedding.provider", havingValue = "tei")
public class TeiEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(TeiEmbeddingProvider.class);

    private final RestClient restClient;
    private final EmbeddingProperties properties;

    /**
     * TEI sağlayıcısını yapılandıran yapıcı metot.
     *
     * @param restClientBuilder Spring RestClient yapıcısı
     * @param properties TEI uç noktası ve model özellikleri
     */
    public TeiEmbeddingProvider(RestClient.Builder restClientBuilder, EmbeddingProperties properties) {
        RestClient.Builder builder = restClientBuilder.baseUrl(properties.getEndpoint());
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            String key = properties.getApiKey().trim();
            String header = properties.getApiKeyHeader() != null ? properties.getApiKeyHeader().trim() : "Authorization";
            if ("Authorization".equalsIgnoreCase(header) && !key.toLowerCase().startsWith("bearer ")) {
                builder.defaultHeader("Authorization", "Bearer " + key);
            } else {
                builder.defaultHeader(header, key);
            }
            builder.defaultHeader("X-API-Key", key);
        }
        this.restClient = builder.build();
        this.properties = properties;
        log.info("TEI embedding sağlayıcısı devrede — endpoint={}, model={}, dims={}, auth={}",
                properties.getEndpoint(), properties.getModel(), properties.getDimensions(),
                (properties.getApiKey() != null && !properties.getApiKey().isBlank()) ? "API-Key aktif" : "yok");
    }

    /**
     * Verilen metni TEI {@code /embed} servisine iletir ve L2 normalize edilmiş vektörünü üretir.
     *
     * @param text Vektörize edilecek metin
     * @return 1024 boyutlu normalize vektör dizisi
     * @throws EmbeddingUnavailableException TEI servisi yanıt vermezse veya hata fırlatırsa
     */
    @Override
    @SuppressWarnings("unchecked")
    public float[] generateEmbedding(String text) {
        try {
            Map<String, Object> body = Map.of("inputs", text);

            List<List<Number>> response = restClient.post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(List.class);

            if (response == null || response.isEmpty()) {
                throw new EmbeddingUnavailableException("TEI servisinden boş yanıt alındı");
            }

            List<Number> vector = (List<Number>) response.getFirst();
            float[] embedding = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                embedding[i] = vector.get(i).floatValue();
            }
            return EmbeddingProvider.normalizeL2(embedding);

        } catch (EmbeddingUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("TEI üzerinden embedding üretimi başarısız: {}", e.getMessage());
            throw new EmbeddingUnavailableException("TEI embedding servisi kullanılamıyor: " + e.getMessage(), e);
        }
    }

    /**
     * Yapılandırılan vektör boyutunu döner.
     *
     * @return Vektör boyutu
     */
    @Override
    public int getDimensions() {
        return properties.getDimensions();
    }

    /**
     * TEI {@code /health} uç noktasına sorgu atarak servisin canlı olup olmadığını test eder.
     *
     * @return Servis sağlıklıysa true, değilse false
     */
    @Override
    public boolean isAvailable() {
        try {
            restClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("TEI sağlık kontrolü başarısız: {}", e.getMessage());
            return false;
        }
    }
}
