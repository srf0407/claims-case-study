package com.claimsprocessing.claims.adapter.out.policymanager;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.time.Duration;

import com.claimsprocessing.claims.application.PolicyAssessmentResult;
import com.claimsprocessing.claims.application.port.out.PolicyManagerPort;
import com.claimsprocessing.claims.application.port.out.PolicyManagerUnavailableException;
import com.claimsprocessing.claims.domain.ClaimType;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

class PolicyManagerClientTest {

    private static final String POLICY_NUMBER = "P-77";

    @RegisterExtension
    static final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private PolicyManagerPort client;

    @BeforeEach
    void setUp() {
        client = new PolicyManagerClient(restClient(Duration.ofSeconds(5)), new PolicyManagerMapper());
    }

    @Test
    void should_return_lapsed_when_status_is_lapsed_and_covered_is_false() {
        // given
        stubCover(200, "{\"policyNumber\":\"P-77\",\"status\":\"LAPSED\",\"covered\":false}");

        // when
        PolicyAssessmentResult result = client.assess(POLICY_NUMBER, ClaimType.DEATH);

        // then
        assertThat(result).isEqualTo(PolicyAssessmentResult.LAPSED);
    }

    @Test
    void should_return_lapsed_when_status_is_lapsed_and_covered_is_missing() {
        // given
        stubCover(200, "{\"policyNumber\":\"P-77\",\"status\":\"LAPSED\"}");

        // when
        PolicyAssessmentResult result = client.assess(POLICY_NUMBER, ClaimType.DEATH);

        // then
        assertThat(result).isEqualTo(PolicyAssessmentResult.LAPSED);
    }

    @Test
    void should_return_covered_when_policy_is_active_and_covered() {
        // given
        stubCover(200, "{\"policyNumber\":\"P-77\",\"status\":\"ACTIVE\",\"covered\":true}");

        // when
        PolicyAssessmentResult result = client.assess(POLICY_NUMBER, ClaimType.DEATH);

        // then
        assertThat(result).isEqualTo(PolicyAssessmentResult.COVERED);
    }

    @Test
    void should_return_no_cover_when_active_policy_is_not_covered() {
        // given
        stubCover(200, "{\"policyNumber\":\"P-77\",\"status\":\"ACTIVE\",\"covered\":false}");

        // when
        PolicyAssessmentResult result = client.assess(POLICY_NUMBER, ClaimType.DEATH);

        // then
        assertThat(result).isEqualTo(PolicyAssessmentResult.NO_COVER);
    }

    @Test
    void should_return_no_cover_when_policy_is_not_found() {
        // given
        wireMock.stubFor(get(urlPathEqualTo("/policies/" + POLICY_NUMBER + "/cover"))
                .willReturn(aResponse().withStatus(404)));

        // when
        PolicyAssessmentResult result = client.assess(POLICY_NUMBER, ClaimType.DEATH);

        // then
        assertThat(result).isEqualTo(PolicyAssessmentResult.NO_COVER);
    }

    @Test
    void should_throw_unavailable_when_policy_manager_returns_server_error() {
        // given
        wireMock.stubFor(get(urlPathEqualTo("/policies/" + POLICY_NUMBER + "/cover"))
                .willReturn(aResponse().withStatus(503)));

        // when / then
        assertUnavailable(() -> client.assess(POLICY_NUMBER, ClaimType.DEATH));
    }

    @Test
    void should_throw_unavailable_when_policy_manager_times_out() {
        // given
        client = new PolicyManagerClient(restClient(Duration.ofMillis(300)), new PolicyManagerMapper());
        wireMock.stubFor(get(urlPathEqualTo("/policies/" + POLICY_NUMBER + "/cover"))
                .willReturn(okJson("{\"policyNumber\":\"P-77\",\"status\":\"ACTIVE\",\"covered\":true}")
                        .withFixedDelay(1500)));

        // when / then
        assertUnavailable(() -> client.assess(POLICY_NUMBER, ClaimType.DEATH));
    }

    @Test
    void should_throw_unavailable_when_policy_manager_resets_the_connection() {
        // given
        wireMock.stubFor(get(urlPathEqualTo("/policies/" + POLICY_NUMBER + "/cover"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        // when / then
        assertUnavailable(() -> client.assess(POLICY_NUMBER, ClaimType.DEATH));
    }

    @Test
    void should_throw_unavailable_when_policy_manager_returns_an_unexpected_status() {
        // given
        wireMock.stubFor(get(urlPathEqualTo("/policies/" + POLICY_NUMBER + "/cover"))
                .willReturn(aResponse().withStatus(400)));

        // when / then
        assertUnavailable(() -> client.assess(POLICY_NUMBER, ClaimType.DEATH));
    }

    @Test
    void should_throw_unavailable_when_active_policy_has_no_covered_field() {
        // given
        stubCover(200, "{\"policyNumber\":\"P-77\",\"status\":\"ACTIVE\"}");

        // when / then
        assertUnavailable(() -> client.assess(POLICY_NUMBER, ClaimType.DEATH));
    }

    @Test
    void should_send_get_cover_for_the_policy_and_claim_type() {
        // given
        stubCover(200, "{\"policyNumber\":\"P-77\",\"status\":\"ACTIVE\",\"covered\":true}");

        // when
        client.assess(POLICY_NUMBER, ClaimType.DEATH);

        // then
        var served = wireMock.getAllServeEvents();
        assertThat(served).hasSize(1);
        var request = served.getFirst().getRequest();
        assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
        assertThat(request.getUrl()).isEqualTo("/policies/P-77/cover?claimType=DEATH");
        assertThat(request.getBodyAsString()).isEmpty();
    }

    private void stubCover(int status, String body) {
        wireMock.stubFor(get(urlPathEqualTo("/policies/" + POLICY_NUMBER + "/cover"))
                .willReturn(okJson(body).withStatus(status)));
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(PolicyManagerUnavailableException.class)
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
