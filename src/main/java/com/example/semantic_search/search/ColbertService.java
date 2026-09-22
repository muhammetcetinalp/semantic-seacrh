package com.example.semantic_search.search;

/**
 * ColBERT servisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.service.ColbertService} paketine taşınmıştır.
 */
@Deprecated
public class ColbertService extends com.example.semantic_search.service.ColbertService {

    public ColbertService(com.example.semantic_search.config.ColbertProperties colbertProperties,
                          com.example.semantic_search.client.colbert.JavaColbertEngine javaColbertEngine) {
        super(colbertProperties, javaColbertEngine);
    }
}
