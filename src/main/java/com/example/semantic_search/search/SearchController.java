package com.example.semantic_search.search;

/**
 * Arama denetleyicisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.controller.SearchController} paketine taşınmıştır.
 */
@Deprecated
public class SearchController extends com.example.semantic_search.controller.SearchController {

    public SearchController(com.example.semantic_search.service.SearchQueryService searchQueryService) {
        super(searchQueryService);
    }
}
