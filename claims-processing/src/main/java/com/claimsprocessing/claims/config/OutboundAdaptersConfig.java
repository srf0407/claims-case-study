package com.claimsprocessing.claims.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import com.claimsprocessing.claims.adapter.out.clientregistry.ClientRegistryClient;
import com.claimsprocessing.claims.adapter.out.clientregistry.ClientRegistryMapper;
import com.claimsprocessing.claims.adapter.out.payment.PaymentClient;
import com.claimsprocessing.claims.adapter.out.payment.PaymentMapper;
import com.claimsprocessing.claims.adapter.out.persistence.InMemoryClaimRepository;
import com.claimsprocessing.claims.adapter.out.policymanager.PolicyManagerClient;
import com.claimsprocessing.claims.adapter.out.policymanager.PolicyManagerMapper;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.ClientRegistryPort;
import com.claimsprocessing.claims.application.port.out.PaymentPort;
import com.claimsprocessing.claims.application.port.out.PolicyManagerPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
        ClientRegistryProperties.class,
        PolicyManagerProperties.class,
        PaymentProperties.class
})
public class OutboundAdaptersConfig {

    @Bean
    RestClient clientRegistryRestClient(ClientRegistryProperties properties) {
        return restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    RestClient policyManagerRestClient(PolicyManagerProperties properties) {
        return restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    RestClient paymentRestClient(PaymentProperties properties) {
        return restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    ClientRegistryPort clientRegistryPort(
            @Qualifier("clientRegistryRestClient") RestClient clientRegistryRestClient) {
        return new ClientRegistryClient(clientRegistryRestClient, new ClientRegistryMapper());
    }

    @Bean
    PolicyManagerPort policyManagerPort(@Qualifier("policyManagerRestClient") RestClient policyManagerRestClient) {
        return new PolicyManagerClient(policyManagerRestClient, new PolicyManagerMapper());
    }

    @Bean
    PaymentPort paymentPort(@Qualifier("paymentRestClient") RestClient paymentRestClient) {
        return new PaymentClient(paymentRestClient, new PaymentMapper());
    }

    @Bean
    ClaimRepositoryPort claimRepositoryPort() {
        return new InMemoryClaimRepository();
    }

    private static RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        factory.enableCompression(false);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .messageConverters(List.of(new JacksonJsonHttpMessageConverter()))
                .build();
    }
}
