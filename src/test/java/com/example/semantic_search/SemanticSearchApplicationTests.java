package com.example.semantic_search;

import com.example.semantic_search.client.opensearch.OpenSearchAdapter;
import com.example.semantic_search.kafka.KafkaIndexingListener;
import com.example.semantic_search.service.SearchEventProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SemanticSearchApplicationTests {

    @Autowired
    private ApplicationContext context;

	@MockitoBean
	private OpenSearchAdapter openSearchAdapter;

	@Test
	void contextLoads() {
		assertThat(context.getBeansOfType(KafkaIndexingListener.class)).isEmpty();
		assertThat(context.getBeansOfType(SearchEventProcessor.class)).isEmpty();
	}
}
