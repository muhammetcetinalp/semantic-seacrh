package com.example.semantic_search.indexing;

/**
 * Olaylar denetleyicisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.controller.OlaylarController} paketine taşınmıştır.
 */
@Deprecated
public class OlaylarController extends com.example.semantic_search.controller.OlaylarController {

    public OlaylarController(com.example.semantic_search.service.OlaylarIngestionService ingestionService) {
        super(ingestionService);
    }
}
