package com.example.semantic_search.indexing;

import org.springframework.data.repository.NoRepositoryBean;

/**
 * İndeksleme durum tablosu deposu.
 *
 * @deprecated Bu arayüz {@link com.example.semantic_search.repository.IndexingStateRepository} paketine taşınmıştır.
 */
@Deprecated
@NoRepositoryBean
public interface IndexingStateRepository extends com.example.semantic_search.repository.IndexingStateRepository {
}
