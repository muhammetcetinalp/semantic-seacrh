package com.example.semantic_search.service;

import com.example.semantic_search.client.colbert.JavaColbertEngine;
import com.example.semantic_search.config.ColbertProperties;
import com.example.semantic_search.dto.HybridExplainResponse;
import com.example.semantic_search.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Üst seviye ColBERT (Contextualized Late Interaction) arama ve yeniden sıralama servisi.
 *
 * <p>Tüm işlemleri harici bir Python bağımlılığı gerektirmeksizin saf Java {@link JavaColbertEngine}
 * üzerinden yürütür. 128 boyutlu çoklu vektörler (multi-vectors) üretir, MaxSim matris puanlaması yapar
 * ve arama sorgusu ile doküman token'ları arasındaki anlamsal eşleşmeleri hesaplar.</p>
 */
@Service
public class ColbertService {

    private static final Logger log = LoggerFactory.getLogger(ColbertService.class);

    private final ColbertProperties colbertProperties;
    private final JavaColbertEngine javaColbertEngine;

    /**
     * ColbertService bileşenini yapılandıran yapıcı metot.
     *
     * @param colbertProperties ColBERT yapılandırma özellikleri
     * @param javaColbertEngine Saf Java ColBERT motoru
     */
    public ColbertService(ColbertProperties colbertProperties, JavaColbertEngine javaColbertEngine) {
        this.colbertProperties = colbertProperties;
        this.javaColbertEngine = javaColbertEngine;
        log.info("Saf Java ColBERT servisi aktif — model={} (Python bağımlılığı yoktur)",
                colbertProperties.getModel());
    }

    /**
     * ColBERT servisinin yapılandırmada etkinleştirilip etkinleştirilmediğini kontrol eder.
     *
     * @return Etkin ise true, aksi halde false
     */
    public boolean isAvailable() {
        return colbertProperties.isEnabled();
    }

    /**
     * Kullanıcı sorgu metnini token'larına ayırıp her token için ColBERT vektörleri üretir.
     *
     * @param query Sorgu metni
     * @return Token başına 128 boyutlu float vektör listesi
     */
    public List<List<Float>> embedQuery(String query) {
        return javaColbertEngine.embedQuery(query);
    }

    /**
     * Doküman başlık ve metin içeriği için çoklu token vektörleri üretir.
     *
     * @param id Doküman ID'si
     * @param title Doküman başlığı
     * @param text Doküman gövde metni
     * @return Token başına 128 boyutlu float vektör listesi
     */
    public List<List<Float>> embedDocument(String id, String title, String text) {
        return javaColbertEngine.embedDocument(id, title, text);
    }

    /**
     * Aday doküman listesini ColBERT MaxSim algoritmasıyla puanlar ve yeniden sıralar.
     *
     * @param query Arama sorgusu
     * @param candidates Aday arama sonuçları listesi
     * @param limit Döndürülecek sonuç limiti
     * @return Yeniden sıralanmış dokümanlar ve açıklama detayları
     */
    public ColbertRankResult scoreAndRank(String query, List<SearchResult> candidates, int limit) {
        var res = javaColbertEngine.scoreAndRank(query, candidates, limit);
        return new ColbertRankResult(res.orderedDocuments(), res.rankedResults(), res.tookMs());
    }

    /**
     * Sorgu token'ları ile doküman token'ları arasındaki en yüksek anlamsal eşleşmeleri tespit eder.
     *
     * @param query Kullanıcı sorgusu
     * @param title Doküman başlığı
     * @param text Doküman metni
     * @return Token bazlı eşleşme listesi
     */
    public List<HybridExplainResponse.TokenMatch> computeTokenMatches(String query, String title, String text) {
        return javaColbertEngine.computeTokenMatches(query, title, text);
    }

    /**
     * ColBERT puanlama sonucunu taşıyan kayıt sınıfı.
     *
     * @param orderedDocuments Sıralanmış arama sonuçları
     * @param rankedResults Sıralanmış açıklama detayları
     * @param tookMs Geçen süre (ms)
     */
    public record ColbertRankResult(
            List<SearchResult> orderedDocuments,
            List<HybridExplainResponse.RankedResult> rankedResults,
            long tookMs
    ) {}
}
