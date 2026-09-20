package com.example.semantic_search.search;

import com.example.semantic_search.configuration.ColbertProperties;
import com.example.semantic_search.search.colbert.JavaColbertEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * High-level ColBERT service. Operates 100% in Java via JavaColbertEngine,
 * producing 128-dimensional multi-vectors and MaxSim scoring without requiring Python.
 */
@Service
public class ColbertService {

    private static final Logger log = LoggerFactory.getLogger(ColbertService.class);

    private final ColbertProperties colbertProperties;
    private final JavaColbertEngine javaColbertEngine;

    public ColbertService(ColbertProperties colbertProperties, JavaColbertEngine javaColbertEngine) {
        this.colbertProperties = colbertProperties;
        this.javaColbertEngine = javaColbertEngine;
        log.info("Pure Java ColBERT service active — model={} (Zero Python dependencies)",
                colbertProperties.getModel());
    }

    public boolean isAvailable() {
        return colbertProperties.isEnabled();
    }

    public List<List<Float>> embedQuery(String query) {
        return javaColbertEngine.embedQuery(query);
    }

    public List<List<Float>> embedDocument(String id, String title, String text) {
        return javaColbertEngine.embedDocument(id, title, text);
    }

    public ColbertRankResult scoreAndRank(String query, List<SearchResult> candidates, int limit) {
        var res = javaColbertEngine.scoreAndRank(query, candidates, limit);
        return new ColbertRankResult(res.orderedDocuments(), res.rankedResults(), res.tookMs());
    }

    public List<HybridExplainResponse.TokenMatch> computeTokenMatches(String query, String title, String text) {
        return javaColbertEngine.computeTokenMatches(query, title, text);
    }

    public record ColbertRankResult(
            List<SearchResult> orderedDocuments,
            List<HybridExplainResponse.RankedResult> rankedResults,
            long tookMs
    ) {}
}
