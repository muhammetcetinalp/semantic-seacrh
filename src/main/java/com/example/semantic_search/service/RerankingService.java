package com.example.semantic_search.service;

import com.example.semantic_search.dto.HybridExplainResponse;

import java.util.List;

/**
 * İlk aşama (first-stage) BM25, Vektör veya Hibrit aramalardan elde edilen aday sonuçları,
 * derin öğrenme tabanlı bir Cross-Encoder (çapraz kodlayıcı) modeli kullanarak
 * sorgu-doküman anlamsal ilgisine göre yeniden puanlayan ve sıralayan (second-stage ranking) servis arayüzü.
 *
 * <p>Bi-Encoder modellerinin aksine, Cross-Encoder sorgu ile doküman metnini tek bir metin çifti
 * olarak alıp tam çapraz dikkat (cross-attention) hesapladığı için çok daha yüksek doğruluk sağlar.</p>
 */
public interface RerankingService {

    /**
     * İlk aşama arama sonuçlarını kullanıcı sorgusuna göre Cross-Encoder ile yeniden sıralar.
     *
     * @param query Kullanıcının orijinal arama sorgusu
     * @param results İlk aşamadan gelen aday füzyon sonuçları listesi
     * @param topN Yeniden sıralama sonrası döndürülecek maksimum sonuç adedi
     * @return Modelin alaka puanına (relevanceScore) göre büyükten küçüğe sıralanmış sonuçlar
     */
    List<RerankedResult> rerank(String query, List<HybridExplainResponse.FusionResult> results, int topN);

    /**
     * Yeniden sıralama servisinin ve arka plandaki model sunucusunun erişilebilir olup olmadığını döner.
     *
     * @return Model sunucusu ayakta ve yanıt veriyorsa true, aksi halde false
     */
    boolean isAvailable();

    /**
     * Yeniden sıralanmış tek bir doküman sonucunu ve Cross-Encoder alaka puanını taşıyan kayıt (record).
     *
     * @param originalIndex Aday dokümanın ilk aşamadaki indeks sırası
     * @param relevanceScore Cross-Encoder modelinin ürettiği derin anlamsal uygunluk puanı
     * @param result Orijinal füzyon sonucu
     */
    record RerankedResult(int originalIndex, double relevanceScore, HybridExplainResponse.FusionResult result) {}
}
