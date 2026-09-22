package com.example.semantic_search.config;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resmi OpenSearch Java istemcisini (OpenSearchClient) ve taşıma katmanını (OpenSearchTransport)
 * başlatan ve Spring konteynerine Bean olarak kaydeden yapılandırma sınıfı.
 *
 * <p>Apache HttpClient 5 tabanlı asenkron/senkron HTTP bağlantı havuzunu, temel kimlik doğrulamayı
 * ve Jackson JSON haritalayıcısını yapılandırır.</p>
 */
@Configuration
@EnableConfigurationProperties({OpenSearchProperties.class, EmbeddingProperties.class, SearchProperties.class})
public class OpenSearchClientConfiguration {

    /**
     * OpenSearch HTTP taşıma katmanını (Transport) yapılandırıp oluşturur.
     *
     * @param properties Yapılandırma dosyasından okunan bağlantı parametreleri
     * @return Yapılandırılmış OpenSearchTransport nesnesi
     */
    @Bean
    public OpenSearchTransport openSearchTransport(OpenSearchProperties properties) {
        HttpHost host = new HttpHost(properties.getScheme(), properties.getHost(), properties.getPort());

        ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder
                .builder(host)
                .setMapper(new JacksonJsonpMapper());

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        new AuthScope(host),
                        new UsernamePasswordCredentials(
                                properties.getUsername(),
                                properties.getPassword().toCharArray()
                        )
                );
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                return httpClientBuilder;
            });
        }

        return builder.build();
    }

    /**
     * OpenSearch üst düzey Java istemcisini (OpenSearchClient) oluşturur.
     *
     * @param transport Yapılandırılmış OpenSearchTransport nesnesi
     * @return Uygulama genelinde kullanılacak OpenSearchClient nesnesi
     */
    @Bean
    public OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }
}
