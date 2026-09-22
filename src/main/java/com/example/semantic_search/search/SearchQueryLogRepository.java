package com.example.semantic_search.search;

import org.springframework.data.repository.NoRepositoryBean;

/**
 * Arama sorgu günlüğü veritabanı deposu.
 *
 * @deprecated Bu arayüz {@link com.example.semantic_search.repository.SearchQueryLogRepository} paketine taşınmıştır.
 */
@Deprecated
@NoRepositoryBean
public interface SearchQueryLogRepository extends com.example.semantic_search.repository.SearchQueryLogRepository {
}
