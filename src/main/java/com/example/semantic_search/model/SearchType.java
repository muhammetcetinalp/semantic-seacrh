package com.example.semantic_search.model;

/**
 * Arama motorunda desteklenen arama algoritmaları ve stratejilerini belirten numaralandırma (Enum).
 *
 * <ul>
 *   <li><b>BM25:</b> Geleneksel ters indeks (inverted index) ve TF-IDF temelli sözcüksel / leksikal arama.</li>
 *   <li><b>SEMANTIC:</b> Derin öğrenme vektör gömmeleri (Dense Embedding) temelli anlamsal arama.</li>
 *   <li><b>HYBRID:</b> BM25 ve Semantik arama sonuçlarının RRF (Reciprocal Rank Fusion) ile birleştirildiği hibrit arama.</li>
 * </ul>
 */
public enum SearchType {

    /** Açık kaynak TF-IDF / BM25 tabanlı sözcüksel arama. */
    BM25,

    /** BGE-M3 tabanlı anlamsal vektör araması. */
    SEMANTIC,

    /** Sözcüksel ve anlamsal sonuçların RRF füzyonu ile harmanlandığı hibrit arama. */
    HYBRID
}
