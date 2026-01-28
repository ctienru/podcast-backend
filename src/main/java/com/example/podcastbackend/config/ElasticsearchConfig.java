package com.example.podcastbackend.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PreDestroy;

import java.util.Base64;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:localhost}")
    private String elasticsearchHost;

    @Value("${elasticsearch.port:9200}")
    private int elasticsearchPort;

    @Value("${elasticsearch.scheme:http}")
    private String elasticsearchScheme;

    @Value("${elasticsearch.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${elasticsearch.socket-timeout:30000}")
    private int socketTimeout;

    // Basic Auth
    @Value("${elasticsearch.username:}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;

    // API Key Auth
    @Value("${elasticsearch.api-key-id:}")
    private String apiKeyId;

    @Value("${elasticsearch.api-key-secret:}")
    private String apiKeySecret;

    private RestClient restClient;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClientBuilder builder = RestClient.builder(
                new HttpHost(elasticsearchHost, elasticsearchPort, elasticsearchScheme)
        ).setRequestConfigCallback(requestConfigBuilder ->
                requestConfigBuilder
                        .setConnectTimeout(connectTimeout)
                        .setSocketTimeout(socketTimeout)
        );

        // Priority: API Key > Basic Auth
        if (hasValue(apiKeyId) && hasValue(apiKeySecret)) {
            // API Key authentication
            String apiKeyCredentials = apiKeyId + ":" + apiKeySecret;
            String encodedApiKey = Base64.getEncoder().encodeToString(apiKeyCredentials.getBytes());
            Header[] defaultHeaders = new Header[]{
                    new BasicHeader("Authorization", "ApiKey " + encodedApiKey)
            };
            builder.setDefaultHeaders(defaultHeaders);
        } else if (hasValue(username) && hasValue(password)) {
            // Basic authentication
            var credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password)
            );
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
            );
        }

        restClient = builder.build();

        ElasticsearchTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper()
        );

        return new ElasticsearchClient(transport);
    }

    private boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }

    @PreDestroy
    public void cleanup() {
        if (restClient != null) {
            try {
                restClient.close();
            } catch (Exception e) {
                // ignore close exception
            }
        }
    }
}
