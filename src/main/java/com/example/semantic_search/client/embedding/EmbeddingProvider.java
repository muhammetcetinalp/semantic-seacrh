package com.example.semantic_search.client.embedding;

/**
 * Vektör gömme (embedding) üretim işlemlerini soyutlayan servis sağlayıcı arayüzü.
 *
 * <p>Vektör gömme modelleri arama motorundan (OpenSearch/Qdrant) tamamen bağımsızdır.
 * Bu sayede HuggingFace TEI, uzaktan REST modelleri (OpenAI vb.) veya yerel test sağlayıcıları
 * sistem konfigürasyonu üzerinden kesintisiz bir şekilde değiştirilebilir.</p>
 */
public interface EmbeddingProvider {

    /**
     * Verilen metin için yoğun (dense) float vektör temsilini üretir ve L2 normalize eder.
     *
     * @param text Vektöre dönüştürülecek metin girdisi
     * @return Birim uzunluğa normalize edilmiş float dizi vektörü
     */
    float[] generateEmbedding(String text);

    /**
     * Sağlayıcının ürettiği vektörlerin boyut sayısını (dimension) döner.
     *
     * @return Vektör boyutu (örneğin 1024)
     */
    int getDimensions();

    /**
     * Vektör sağlayıcı servisinin ayakta ve erişilebilir olup olmadığını kontrol eder.
     *
     * @return Servis erişilebilir ise true, aksi halde false
     */
    boolean isAvailable();

    /**
     * Verilen float vektörünü birim uzunluğa (L2 norm = 1.0) normalize eder.
     *
     * <p>FAISS ve k-NN kosinüs benzerliği aramalarında sayısal kararlılığı ve
     * doğruluk derecesini optimize eder.</p>
     *
     * @param vector Normalize edilecek ilkel float dizisi
     * @return L2 normu 1.0 olan float dizisi
     */
    static float[] normalizeL2(float[] vector) {
        if (vector == null || vector.length == 0) {
            return vector;
        }
        double sumSq = 0.0;
        for (float v : vector) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 1e-9) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }
}
