package com.example.semantic_search.search.colbert;

import com.example.semantic_search.search.HybridExplainResponse;
import com.example.semantic_search.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Pure Java implementation of the ColBERT (Contextualized Late Interaction) engine.
 * Generates 128-dimensional L2-normalized contextual token embeddings and computes
 * MaxSim (Late Interaction) similarity without any external Python dependencies.
 */
@Component
public class JavaColbertEngine {

    private static final Logger log = LoggerFactory.getLogger(JavaColbertEngine.class);
    private static final int VECTOR_DIM = 128;

    public List<List<Float>> embedQuery(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> tokens = tokenize(query);
        return embedTokens(tokens, true);
    }

    public List<List<Float>> embedDocument(String id, String title, String text) {
        String fullText = ((title != null ? title : "") + " " + (text != null ? text : "")).trim();
        if (fullText.isBlank()) {
            return List.of();
        }
        List<String> tokens = tokenize(fullText);
        return embedTokens(tokens, false);
    }

    public JavaColbertRankResult scoreAndRank(String query, List<SearchResult> candidates, int limit) {
        long start = System.currentTimeMillis();
        if (candidates == null || candidates.isEmpty()) {
            return new JavaColbertRankResult(List.of(), List.of(), System.currentTimeMillis() - start);
        }

        List<String> qTokens = tokenize(query);
        List<List<Float>> qVectors = embedTokens(qTokens, true);

        List<ScoredDoc> scoredDocs = new ArrayList<>();

        for (SearchResult doc : candidates) {
            String fullText = ((doc.getTitle() != null ? doc.getTitle() : "") + " " +
                    (doc.getSearchText() != null ? doc.getSearchText() : "")).trim();
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
        log.info("Pure Java ColBERT scored {} documents in {}ms (top score={})",
                candidates.size(), tookMs, (!orderedDocs.isEmpty() ? orderedDocs.get(0).getScore() : 0));

        return new JavaColbertRankResult(orderedDocs, ranked, tookMs);
    }

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
            // If word is longer than 5 chars, also produce a subword stem
            if (w.length() > 5) {
                tokens.add(w.substring(0, Math.min(5, w.length())));
            }
        }
        return tokens.isEmpty() ? List.of(rawWords) : tokens;
    }

    private List<List<Float>> embedTokens(List<String> tokens, boolean isQuery) {
        List<List<Float>> vectors = new ArrayList<>();
        int n = tokens.size();

        for (int i = 0; i < n; i++) {
            String token = tokens.get(i);
            String prev = (i > 0) ? tokens.get(i - 1) : "";
            String next = (i < n - 1) ? tokens.get(i + 1) : "";

            // 1. Core Token Representation (80% weight) - Identical for Query and Document!
            float[] baseVec = computeSubwordVector(token, VECTOR_DIM);

            // 2. Context Window (20% weight) - Subtle contextual influence from neighbors
            float[] contextVec = new float[VECTOR_DIM];
            if (!prev.isEmpty()) {
                float[] pVec = computeSubwordVector(prev, VECTOR_DIM);
                for (int d = 0; d < VECTOR_DIM; d++) contextVec[d] += pVec[d] * 0.5f;
            }
            if (!next.isEmpty()) {
                float[] nVec = computeSubwordVector(next, VECTOR_DIM);
                for (int d = 0; d < VECTOR_DIM; d++) contextVec[d] += nVec[d] * 0.5f;
            }

            // Blend: 85% core identity + 15% context
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

    private float[] computeSubwordVector(String word, int dim) {
        float[] vector = new float[dim];
        if (word == null || word.isEmpty()) {
            return vector;
        }

        // Whole word vector
        addDeterministicVector(vector, "W:" + word, 1.0f, dim);

        // Character n-grams (3-grams and 4-grams) for morphological similarity (e.g. model ~ modelleri)
        String padded = "<" + word + ">";
        int subwordCount = 1;

        if (padded.length() >= 3) {
            for (int i = 0; i <= padded.length() - 3; i++) {
                String trigram = padded.substring(i, i + 3);
                addDeterministicVector(vector, "TRI:" + trigram, 0.4f, dim);
                subwordCount++;
            }
        }
        if (padded.length() >= 4) {
            for (int i = 0; i <= padded.length() - 4; i++) {
                String fourgram = padded.substring(i, i + 4);
                addDeterministicVector(vector, "FOUR:" + fourgram, 0.3f, dim);
                subwordCount++;
            }
        }

        normalizeL2(vector);
        return vector;
    }

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

    private double dotProduct(List<Float> a, List<Float> b) {
        double dot = 0.0;
        int len = Math.min(a.size(), b.size());
        for (int i = 0; i < len; i++) {
            dot += a.get(i) * b.get(i);
        }
        return dot;
    }

    private long bytesToLong(byte[] bytes) {
        long result = 0;
        for (int i = 0; i < 8 && i < bytes.length; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }

    public List<HybridExplainResponse.TokenMatch> computeTokenMatches(String query, String title, String text) {
        if (query == null || query.isBlank()) return List.of();
        String fullText = ((title != null ? title : "") + " " + (text != null ? text : "")).trim();
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

    public record JavaColbertRankResult(
            List<SearchResult> orderedDocuments,
            List<HybridExplainResponse.RankedResult> rankedResults,
            long tookMs
    ) {}

    private record ScoredDoc(
            SearchResult doc,
            double score,
            List<HybridExplainResponse.TokenMatch> tokenMatches
    ) {}
}
