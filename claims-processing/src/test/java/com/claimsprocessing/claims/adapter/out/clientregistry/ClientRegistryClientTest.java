package com.claimsprocessing.claims.adapter.out.clientregistry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.time.Duration;

import com.claimsprocessing.claims.application.ClientCheckResult;
import com.claimsprocessing.claims.application.port.out.ClientRegistryPort;
import com.claimsprocessing.claims.application.port.out.ClientRegistryUnavailableException;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

class ClientRegistryClientTest {

    private static final String CLIENT_ID = "C-1";

    @RegisterExtension
    static final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private ClientRegistryPort client;

    @BeforeEach
    void setUp() {
        client = new ClientRegistryClient(restClient(Duration.ofSeconds(5)), new ClientRegistryMapper());
    }

    @Test
    void should_return_known_when_the_registry_returns_active() {
        // given
        wireMock.stubFor(get(urlEqualTo("/clients/" + CLIENT_ID))
                .willReturn(okJson("{\"clientId\":\"C-1\",\"status\":\"ACTIVE\"}")));

        // when
        ClientCheckResult result = client.check(CLIENT_ID);

        // then
        assertThat(result).isEqualTo(ClientCheckResult.KNOWN);
    }

    @Test
    void should_return_unknown_when_the_client_is_missing() {
        // given
        wireMock.stubFor(get(urlEqualTo("/clients/" + CLIENT_ID))
                .willReturn(aResponse().withStatus(404)));

        // when
        ClientCheckResult result = client.check(CLIENT_ID);

        // then
        assertThat(result).isEqualTo(ClientCheckResult.UNKNOWN);
    }

    @Test
    void should_throw_unavailable_when_the_registry_returns_503() {
        // given
        wireMock.stubFor(get(urlEqualTo("/clients/" + CLIENT_ID))
                .willReturn(aResponse().withStatus(503)));

        // when / then
        assertUnavailable(() -> client.check(CLIENT_ID));
    }

    @Test
    void should_throw_unavailable_on_an_unexpected_status() {
        // given
        wireMock.stubFor(get(urlEqualTo("/clients/" + CLIENT_ID))
                .willReturn(aResponse().withStatus(400)));

        // when / then
        assertUnavailable(() -> client.check(CLIENT_ID));
    }

    @Test
    void should_throw_unavailable_when_the_read_times_out() {
        // given
        client = new ClientRegistryClient(restClient(Duration.ofMillis(300)), new ClientRegistryMapper());
        wireMock.stubFor(get(urlEqualTo("/clients/" + CLIENT_ID))
                .willReturn(okJson("{\"clientId\":\"C-1\",\"status\":\"ACTIVE\"}").withFixedDelay(1500)));

        // when / then
        assertUnavailable(() -> client.check(CLIENT_ID));
    }

    @Test
    void should_throw_unavailable_when_the_connection_fails() {
        // given
        wireMock.stubFor(get(urlEqualTo("/clients/" + CLIENT_ID))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        // when / then
        assertUnavailable(() -> client.check(CLIENT_ID));
    }

    @Test
    void should_send_get_clients_with_an_empty_body() {
        // given
        wireMock.stubFor(get(urlEqualTo("/clients/" + CLIENT_ID))
                .willReturn(okJson("{\"clientId\":\"C-1\",\"status\":\"ACTIVE\"}")));

        // when
        client.check(CLIENT_ID);

        // then
        var served = wireMock.getAllServeEvents();
        assertThat(served).hasSize(1);
        var request = served.getFirst().getRequest();
        assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
        assertThat(request.getUrl()).isEqualTo("/clients/" + CLIENT_ID);
        assertThat(request.getBodyAsString()).isEmpty();
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ClientRegistryUnavailableException.class)
                .extracting(Throwable::getCause)
                .isNotNull();
    }

    private static RestClient restClient(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        factory.enableCompression(false);
        return RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .requestFactory(factory)
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new JacksonJsonHttpMessageConverter());
                })
                .build();
    }
}
