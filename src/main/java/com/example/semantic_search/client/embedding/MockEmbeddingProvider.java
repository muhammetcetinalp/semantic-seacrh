package com.example.semantic_search.client.embedding;

import com.example.semantic_search.config.EmbeddingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Geliştirme, birim testi ve harici model servislerinin bulunmadığı ortamlar için tasarlanmış
 * deterministik mock vektör üreticisi.
 *
 * <p>Metnin hash kodunu rastgele sayı üretecinin tohumu (seed) olarak kullanarak aynı metin girdisi için
 * her zaman birebir aynı normalize vektörün üretilmesini garanti eder.</p>
 */
@Component
@ConditionalOnProperty(
        name = "search.embedding.provider",
        havingValue = "mock",
        matchIfMissing = true)
public class MockEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(MockEmbeddingProvider.class);

    private final int dimensions;

    /**
     * Vektör boyutunu yapılandırmadan okuyan yapıcı metot.
     *
     * @param properties Gömme özellikleri
     */
    public MockEmbeddingProvider(EmbeddingProperties properties) {
        this.dimensions = properties.getDimensions();
        log.info("Mock embedding sağlayıcısı aktif — boyut={}", dimensions);
    }

    /**
     * Verilen metnin hash kodunu tohum alarak deterministik ve normalize bir float vektörü oluşturur.
     *
     * @param text Girdi metni
     * @return Deterministik normalize float vektörü
     */
    @Override
    public float[] generateEmbedding(String text) {
        Random rng = new Random(text.hashCode());
        float[] embedding = new float[dimensions];
        float norm = 0f;
        for (int i = 0; i < dimensions; i++) {
            embedding[i] = rng.nextFloat() * 2 - 1;
            norm += embedding[i] * embedding[i];
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < dimensions; i++) {
            embedding[i] /= norm;
        }
        return embedding;
    }

    /**
     * Vektör boyut sayısını döner.
     *
     * @return Boyut sayısı
     */
    @Override
    public int getDimensions() {
        return dimensions;
    }

    /**
     * Mock sağlayıcı her zaman kullanılabilir durumdadır.
     *
     * @return Her zaman true
     */
    @Override
    public boolean isAvailable() {
        return true;
    }
}
