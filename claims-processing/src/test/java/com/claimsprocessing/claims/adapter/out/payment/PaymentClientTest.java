package com.claimsprocessing.claims.adapter.out.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.out.PaymentPort;
import com.claimsprocessing.claims.application.port.out.PaymentRequest;
import com.claimsprocessing.claims.application.port.out.PaymentUnavailableException;
import com.claimsprocessing.claims.domain.ClaimId;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

class PaymentClientTest {

    private static final PaymentRequest REQUEST = new PaymentRequest(
            "idem-001",
            new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
            "C-100",
            "P-200",
            new BigDecimal("10.50"));

    @RegisterExtension
    static final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private PaymentPort client;

    @BeforeEach
    void setUp() {
        client = new PaymentClient(restClient(Duration.ofSeconds(5)), new PaymentMapper());
    }

    @Test
    void should_return_when_payment_accepts_with_202() {
        // given
        stubStatus(202);

        // when / then
        assertThatCode(() -> client.initiate(REQUEST)).doesNotThrowAnyException();
    }

    @Test
    void should_return_when_payment_already_exists_with_409() {
        // given
        stubStatus(409);

        // when / then
        assertThatCode(() -> client.initiate(REQUEST)).doesNotThrowAnyException();
    }

    @Test
    void should_throw_payment_unavailable_when_payment_returns_503() {
        // given
        stubStatus(503);

        // when / then
        assertUnavailable(() -> client.initiate(REQUEST));
    }

    @Test
    void should_throw_payment_unavailable_when_payment_returns_200() {
        // given
        stubStatus(200);

        // when / then
        assertUnavailable(() -> client.initiate(REQUEST));
    }

    @Test
    void should_throw_payment_unavailable_when_the_read_times_out() {
        // given
        client = new PaymentClient(restClient(Duration.ofMillis(300)), new PaymentMapper());
        wireMock.stubFor(post(urlEqualTo("/payments"))
                .willReturn(aResponse().withStatus(202).withFixedDelay(1500)));

        // when / then
        assertUnavailable(() -> client.initiate(REQUEST));
    }

    @Test
    void should_throw_payment_unavailable_when_the_connection_fails() {
        // given
        wireMock.stubFor(post(urlEqualTo("/payments"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        // when / then
        assertUnavailable(() -> client.initiate(REQUEST));
    }

    @Test
    void should_post_the_payment_request_with_the_idempotency_key() {
        // given
        stubStatus(202);

        // when
        client.initiate(REQUEST);

        // then
        var served = wireMock.getAllServeEvents();
        assertThat(served).hasSize(1);
        var request = served.getFirst().getRequest();
        assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
        assertThat(request.getUrl()).isEqualTo("/payments");
        assertThat(request.getHeader(PaymentClient.IDEMPOTENCY_KEY_HEADER)).isEqualTo("idem-001");
        assertThat(request.getHeader("Content-Type")).contains("application/json");
        assertThat(request.getBodyAsString()).contains("\"claimId\":\"11111111-1111-1111-1111-111111111111\"");
        assertThat(request.getBodyAsString()).contains("\"clientId\":\"C-100\"");
        assertThat(request.getBodyAsString()).contains("\"policyNumber\":\"P-200\"");
        assertThat(request.getBodyAsString()).contains("\"amount\":10.50");
    }

    private static void stubStatus(int status) {
        wireMock.stubFor(post(urlEqualTo("/payments")).willReturn(aResponse().withStatus(status)));
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(PaymentUnavailableException.class)
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
