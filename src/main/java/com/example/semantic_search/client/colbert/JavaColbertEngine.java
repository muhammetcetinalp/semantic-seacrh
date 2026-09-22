package com.example.semantic_search.client.colbert;

import com.example.semantic_search.dto.HybridExplainResponse;
import com.example.semantic_search.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Harici Python veya PyTorch bağımlılığı olmaksızın çalışan saf Java ColBERT
 * (Contextualized Late Interaction over BERT) arama ve yeniden sıralama motoru.
 *
 * <p>Bu sınıf şu temel yetenekleri sunar:
 * <ul>
 *   <li>128 boyutlu, L2 normalize edilmiş, bağlamsal (contextual window) token vektörleri üretimi.</li>
 *   <li>Kavramsal anlamsal kümeleme (Domain Concept Mapping) ile Türkçe eşanlamlı kelimeler arasında kosinüs yakınlığı sağlama.</li>
 *   <li>Karakter n-gram (3-gram ve 4-gram) alt-kelime vektörleri ile morfolojik Türkçe çekim eklerine tolerans.</li>
 *   <li>Geç etkileşimli MaxSim (Late Interaction) matris skoru hesaplama ve açıklanabilirlik (explainability) token eşleşmeleri üretme.</li>
 * </ul>
 * </p>
 */
@Component
public class JavaColbertEngine {

    private static final Logger log = LoggerFactory.getLogger(JavaColbertEngine.class);
    private static final int VECTOR_DIM = 128;

    /**
     * Qdrant bellek taşmasını engellemek ve MaxSim matris işlemlerini hızlı tutmak için
     * sorgu başına belirlenen maksimum token tavanı.
     */
    public static final int MAX_QUERY_TOKENS = 32;

    /**
     * Doküman başına belirlenen maksimum token tavanı.
     */
    public static final int MAX_DOC_TOKENS = 256;

    /**
     * Eşanlamlı veya yakın kavramlar arasındaki anlamsal bağı güçlendiren kavram haritası.
     */
    private static final Map<String, String> CONCEPT_MAP = new HashMap<>();

    static {
        // Yaya / Yol / Kaldırım
        registerConcept("CONCEPT:WALKWAY", "kaldırım", "kaldırımı", "kaldırımlar", "kaldırımda",
                "tretuvar", "yol", "kenarı", "kenar", "yolkenarı", "yaya", "patika", "yürüyüş");
        // Taşıt / Araç
        registerConcept("CONCEPT:VEHICLE", "araç", "araba", "otomobil", "vasıta", "taşıt",
                "kamyon", "tır", "otobüs", "minibüs", "motosiklet", "motor");
        // Deniz / Gemi / Bot
        registerConcept("CONCEPT:VESSEL", "gemi", "tekne", "bot", "fırkateyn", "hücumbot",
                "korvet", "tanker", "kargo", "sandal", "yat", "vapur", "feribot");
        // Kaza / Çarpışma / Hasar
        registerConcept("CONCEPT:ACCIDENT", "kaza", "kazası", "çarpışma", "devrilme", "kırım",
                "hasar", "kazaen", "çarpma", "savrulma");
        // Yangın / Alev / İtfaiye
        registerConcept("CONCEPT:FIRE", "yangın", "yangını", "alev", "duman", "itfaiye",
                "yanma", "kundaklama", "patlama", "infilak");
        // Güvenlik / Kolluk / Asayiş
        registerConcept("CONCEPT:SECURITY", "emniyet", "polis", "jandarma", "sahil", "güvenlik",
                "komutanlığı", "karakol", "asayiş", "devriye", "muhafaza");
        // Arama Kurtarma / Müdahale
        registerConcept("CONCEPT:RESCUE", "kurtarma", "tahliye", "yardım", "arama", "müdahale",
                "afad", "sedyeyle", "sağ", "yaralı");
        // Şüpheli / Kaçak / İhlal
        registerConcept("CONCEPT:SUSPICIOUS", "şüpheli", "kaçak", "ihlal", "izinsiz",
                "kaçakçılık", "firar", "yetkisiz", "zanlı");
        // Denizcilik / Boğaz / Liman / Kıyı
        registerConcept("CONCEPT:MARITIME", "boğaz", "boğazı", "liman", "marina", "rıhtım",
                "iskele", "açıkları", "deniz", "kıyı", "koyu", "kıyıdan");
        // Hava Taşıtları
        registerConcept("CONCEPT:AERIAL", "uçak", "helikopter", "iha", "siha", "dron", "drone",
                "hava", "uçuş");
    }

    /**
     * Verilen kelimeleri belirli bir kavram anahtarıyla kavram haritasına kaydeder.
     *
     * @param conceptKey Kavram kodu
     * @param words İlişkili kelimeler
     */
    private static void registerConcept(String conceptKey, String... words) {
        for (String w : words) {
            CONCEPT_MAP.put(w.toLowerCase(Locale.forLanguageTag("tr")), conceptKey);
        }
    }

    /**
     * Arama sorgusunu token'larına ayırıp her token için bağlamsal ColBERT vektörleri üretir.
     *
     * @param query Kullanıcı sorgu metni
     * @return Token başına 128 boyutlu float vektör listesi
     */
    public List<List<Float>> embedQuery(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> tokens = tokenize(query);
        return embedTokens(tokens, true);
    }

    /**
     * Dokümanın başlık ve metin içeriğini birleştirip ColBERT doküman token vektörlerini üretir.
     *
     * @param id Doküman ID'si
     * @param title Doküman başlığı
     * @param text Doküman metni
     * @return Token başına 128 boyutlu vektör listesi
     */
    public List<List<Float>> embedDocument(String id, String title, String text) {
        String fullText = resolveFullText(title, text);
        if (fullText.isBlank()) {
            return List.of();
        }
        List<String> tokens = tokenize(fullText);
        return embedTokens(tokens, false);
    }

    /**
     * Aday doküman kümesini sorgu ile MaxSim geç etkileşim (late interaction) algoritmasına tabi tutarak yeniden sıralar.
     *
     * @param query Arama sorgusu
     * @param candidates OpenSearch veya ön aşamadan gelen aday dokümanlar
     * @param limit Sıralamada dönecek doküman sayısı
     * @return Yeniden sıralanmış dokümanlar ve token bazlı eşleşme detayları
     */
    public JavaColbertRankResult scoreAndRank(String query, List<SearchResult> candidates, int limit) {
        long start = System.currentTimeMillis();
        if (candidates == null || candidates.isEmpty()) {
            return new JavaColbertRankResult(List.of(), List.of(), System.currentTimeMillis() - start);
        }

        List<String> qTokens = tokenize(query);
        List<List<Float>> qVectors = embedTokens(qTokens, true);

        List<ScoredDoc> scoredDocs = new ArrayList<>();

        for (SearchResult doc : candidates) {
            String candidateText = doc.getSearchText();
            if (candidateText == null || candidateText.isBlank()) {
                candidateText = (doc.getShortText() != null ? doc.getShortText() : "") + " " +
                        (doc.getLongText() != null ? doc.getLongText() : "");
            }
            String fullText = resolveFullText(doc.getTitle(), candidateText);
            List<String> dTokens = tokenize(fullText);
            List<List<Float>> dVectors = embedTokens(dTokens, false);

            double totalMaxSim = 0.0;
            List<HybridExplainResponse.TokenMatch> tokenMatches = new ArrayList<>();

            for (int qIdx = 0; qIdx < qTokens.size(); qIdx++) {
                String qTok = qTokens.get(qIdx);
                List<Float> qVec = qVectors.get(qIdx);

                double maxSim = -1.0;
                String bestDocTok = "";

                for (int dIdx = 0; dIdx < dTokens.size(); dIdx++) {
                    String dTok = dTokens.get(dIdx);
                    List<Float> dVec = dVectors.get(dIdx);

                    double sim = dotProduct(qVec, dVec);
                    if (sim > maxSim) {
                        maxSim = sim;
                        bestDocTok = dTok;
                    }
                }

                if (maxSim > 0) {
                    totalMaxSim += maxSim;
                    tokenMatches.add(new HybridExplainResponse.TokenMatch(
                            qTok,
                            bestDocTok,
                            Math.round(maxSim * 10000.0) / 10000.0
                    ));
                }
            }

            double finalScore = Math.round(totalMaxSim * 1000.0) / 1000.0;
            scoredDocs.add(new ScoredDoc(doc, finalScore, tokenMatches));
        }

        scoredDocs.sort((a, b) -> Double.compare(b.score(), a.score()));

        List<SearchResult> orderedDocs = new ArrayList<>();
        List<HybridExplainResponse.RankedResult> ranked = new ArrayList<>();

        int top = Math.min(limit, scoredDocs.size());
        for (int i = 0; i < top; i++) {
            ScoredDoc sd = scoredDocs.get(i);
            sd.doc().setScore(sd.score());
            orderedDocs.add(sd.doc());
            ranked.add(new HybridExplainResponse.RankedResult(
                    i + 1,
                    sd.score(),
                    sd.doc(),
                    sd.tokenMatches()
            ));
        }

        long tookMs = System.currentTimeMillis() - start;
        log.info("Pure Java ColBERT {} adet dokümanı {}ms sürede puanladı (en yüksek skor={})",
                candidates.size(), tookMs, (!orderedDocs.isEmpty() ? orderedDocs.get(0).getScore() : 0));

        return new JavaColbertRankResult(orderedDocs, ranked, tookMs);
    }

    /**
     * Türkçe karakter setini koruyarak metni küçük harfe dönüştürür ve noktalama işaretlerinden arındırarak kelimelere böler.
     *
     * @param text Ayrıştırılacak metin
     * @return Token listesi
     */
    public List<String> tokenize(String text) {
        if (text == null) return List.of();
        String normalized = text.toLowerCase(Locale.forLanguageTag("tr"))
                .replaceAll("[^a-z0-9çğıöşüâîû\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isEmpty()) return List.of();

        String[] rawWords = normalized.split(" ");
        List<String> tokens = new ArrayList<>();

        for (String w : rawWords) {
            if (w.length() <= 1) continue;
            tokens.add(w);
        }
        return tokens.isEmpty() ? List.of(rawWords) : tokens;
    }

    /**
     * Token dizisini bağlamsal komşuluk penceresi ve alt-kelime vektörleriyle harmanlayarak
     * ColBERT çoklu vektörlerine çevirir.
     *
     * @param tokens Token listesi
     * @param isQuery Sorgu mu doküman mı olduğu bayrağı
     * @return Vektör dizileri listesi
     */
    private List<List<Float>> embedTokens(List<String> tokens, boolean isQuery) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }

        int maxTokens = isQuery ? MAX_QUERY_TOKENS : MAX_DOC_TOKENS;
        List<String> cappedTokens = tokens.size() > maxTokens ? tokens.subList(0, maxTokens) : tokens;

        List<List<Float>> vectors = new ArrayList<>();
        int n = cappedTokens.size();

        for (int i = 0; i < n; i++) {
            String token = cappedTokens.get(i);
            String prev = (i > 0) ? cappedTokens.get(i - 1) : "";
            String next = (i < n - 1) ? cappedTokens.get(i + 1) : "";

            // 1. Temel Token Temsili (%80 ağırlık)
            float[] baseVec = computeSubwordVector(token, VECTOR_DIM);

            // 2. Bağlamsal Komşuluk Penceresi (%20 ağırlık)
            float[] contextVec = new float[VECTOR_DIM];
            if (!prev.isEmpty()) {
                float[] pVec = computeSubwordVector(prev, VECTOR_DIM);
                for (int d = 0; d < VECTOR_DIM; d++) contextVec[d] += pVec[d] * 0.5f;
            }
            if (!next.isEmpty()) {
                float[] nVec = computeSubwordVector(next, VECTOR_DIM);
                for (int d = 0; d < VECTOR_DIM; d++) contextVec[d] += nVec[d] * 0.5f;
            }

            // Harmanlama: %85 temel kimlik + %15 bağlam
            float[] blended = new float[VECTOR_DIM];
            for (int d = 0; d < VECTOR_DIM; d++) {
                blended[d] = (baseVec[d] * 0.85f) + (contextVec[d] * 0.15f);
            }

            normalizeL2(blended);

            List<Float> vecList = new ArrayList<>(VECTOR_DIM);
            for (float v : blended) {
                vecList.add(v);
            }
            vectors.add(vecList);
        }
        return vectors;
    }

    /**
     * Tek bir kelime için alt-kelime n-gramlarını, kavram kümesini ve tam kelime hash'ini
     * birleştirerek deterministik bir float vektörü oluşturur.
     *
     * @param word Hedef kelime
     * @param dim Hedef boyut (128)
     * @return Normalize float vektörü
     */
    private float[] computeSubwordVector(String word, int dim) {
        float[] vector = new float[dim];
        if (word == null || word.isEmpty()) {
            return vector;
        }

        // Tam kelime vektörü
        addDeterministicVector(vector, "W:" + word, 1.0f, dim);

        // Kavram kümesi vektörü (anlamsal eşanlamlılık enjeksiyonu)
        String concept = CONCEPT_MAP.get(word);
        if (concept != null) {
            addDeterministicVector(vector, concept, 0.6f, dim);
        }

        // Karakter 3-gram ve 4-gram alt kelimeleri
        String padded = "<" + word + ">";

        if (padded.length() >= 3) {
            for (int i = 0; i <= padded.length() - 3; i++) {
                String trigram = padded.substring(i, i + 3);
                addDeterministicVector(vector, "TRI:" + trigram, 0.35f, dim);
            }
        }
        if (padded.length() >= 4) {
            for (int i = 0; i <= padded.length() - 4; i++) {
                String fourgram = padded.substring(i, i + 4);
                addDeterministicVector(vector, "FOUR:" + fourgram, 0.25f, dim);
            }
        }

        normalizeL2(vector);
        return vector;
    }

    /**
     * Belirtilen anahtar için SHA-256 tabanlı deterministik rastgele vektör üretip hedef diziye ekler.
     *
     * @param target Hedef float dizisi
     * @param key Deterministik tohum anahtarı
     * @param weight Katkı katsayısı
     * @param dim Boyut adedi
     */
    private void addDeterministicVector(float[] target, String key, float weight, int dim) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(key.getBytes(StandardCharsets.UTF_8));
            Random rng = new Random(bytesToLong(hash));
            for (int i = 0; i < dim; i++) {
                target[i] += ((float) rng.nextGaussian()) * weight;
            }
        } catch (Exception e) {
            for (int i = 0; i < dim; i++) {
                target[i] += ((float) Math.sin(key.hashCode() * (i + 1))) * weight;
            }
        }
    }

    /**
     * Float dizisini yerinde L2 normuna göre normalize eder.
     *
     * @param v Normalize edilecek dizi
     */
    private void normalizeL2(float[] v) {
        double sumSq = 0.0;
        for (float val : v) {
            sumSq += val * val;
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 1e-9) {
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
    }

    /**
     * İki float listesi arasında nokta çarpımı (dot product) hesaplar.
     *
     * @param a Birinci vektör
     * @param b İkinci vektör
     * @return Nokta çarpım değeri (kosinüs benzerliği)
     */
    private double dotProduct(List<Float> a, List<Float> b) {
        double dot = 0.0;
        int len = Math.min(a.size(), b.size());
        for (int i = 0; i < len; i++) {
            dot += a.get(i) * b.get(i);
        }
        return dot;
    }

    /**
     * Bayt dizisinin ilk 8 baytını long tipine çevirir.
     *
     * @param bytes Bayt dizisi
     * @return Long tam sayı
     */
    private long bytesToLong(byte[] bytes) {
        long result = 0;
        for (int i = 0; i < 8 && i < bytes.length; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }

    /**
     * Açıklanabilirlik paneli için sorgu ve doküman token'ları arasındaki en yüksek benzerlik eşleşmelerini döner.
     *
     * @param query Sorgu metni
     * @param title Doküman başlığı
     * @param text Doküman gövdesi
     * @return TokenMatch listesi
     */
    public List<HybridExplainResponse.TokenMatch> computeTokenMatches(String query, String title, String text) {
        if (query == null || query.isBlank()) return List.of();
        String fullText = resolveFullText(title, text);
        if (fullText.isBlank()) return List.of();

        List<String> qTokens = tokenize(query);
        List<String> dTokens = tokenize(fullText);
        List<List<Float>> qVectors = embedTokens(qTokens, true);
        List<List<Float>> dVectors = embedTokens(dTokens, false);

        List<HybridExplainResponse.TokenMatch> tokenMatches = new ArrayList<>();
        for (int qIdx = 0; qIdx < qTokens.size(); qIdx++) {
            String qTok = qTokens.get(qIdx);
            List<Float> qVec = qVectors.get(qIdx);

            double maxSim = -1.0;
            String bestDocTok = "";
            for (int dIdx = 0; dIdx < dTokens.size(); dIdx++) {
                String dTok = dTokens.get(dIdx);
                List<Float> dVec = dVectors.get(dIdx);
                double sim = dotProduct(qVec, dVec);
                if (sim > maxSim) {
                    maxSim = sim;
                    bestDocTok = dTok;
                }
            }
            if (maxSim > 0.3) {
                tokenMatches.add(new HybridExplainResponse.TokenMatch(
                        qTok, bestDocTok, Math.round(maxSim * 10000.0) / 10000.0
                ));
            }
        }
        return tokenMatches;
    }

    /**
     * Başlık metin içinde zaten yer alıyorsa mükerrer olmasını engelleyerek tam metni çözer.
     *
     * @param title Başlık
     * @param text Metin içeriği
     * @return Çözümlenmiş birleşik metin
     */
    private String resolveFullText(String title, String text) {
        if (text == null || text.isBlank()) {
            return title != null ? title.trim() : "";
        }
        if (title == null || title.isBlank() || text.contains(title)) {
            return text.trim();
        }
        return (title.trim() + " " + text.trim()).trim();
    }

    /**
     * ColBERT yeniden sıralama işlem sonucunu taşıyan kayıt (record).
     *
     * @param orderedDocuments Sıralanmış dokümanlar
     * @param rankedResults Sıralanmış açıklama detayları
     * @param tookMs İşlemin sürdüğü milisaniye süresi
     */
    public record JavaColbertRankResult(
            List<SearchResult> orderedDocuments,
            List<HybridExplainResponse.RankedResult> rankedResults,
            long tookMs
    ) {}

    /**
     * Puanlanmış doküman ara verisini temsil eden dahili kayıt.
     *
     * @param doc Arama sonucu dokümanı
     * @param score MaxSim skoru
     * @param tokenMatches Token eşleşmeleri
     */
    private record ScoredDoc(
            SearchResult doc,
            double score,
            List<HybridExplainResponse.TokenMatch> tokenMatches
    ) {}
}
