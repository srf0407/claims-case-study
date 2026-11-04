package com.claimsprocessing.claims;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.claimsprocessing.claims.adapter.in.web.PaymentSignatureVerifier;
import com.claimsprocessing.claims.application.ClaimWorkerResult;
import com.claimsprocessing.claims.application.port.in.ClaimWorkerUseCase;
import com.claimsprocessing.claims.config.PaymentProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "claims.death-sla.check-interval=PT24H",
        "claims.worker.poll-interval=PT24H"
})
class ClaimFlowEndToEndTest {

    private static final String FUNERAL = """
            {"claimType":"FUNERAL","clientId":"C-1","policyNumber":"P-1","amount":150.00,"incidentDate":"2026-03-01"}
            """;
    private static final String DEATH = """
            {"claimType":"DEATH","clientId":"C-1","policyNumber":"P-1","amount":150.00,"incidentDate":"2026-03-01","dateOfDeath":"2026-03-01","deathCertificateRef":"DC-1"}
            """;
    private static final String COVERED = """
            {"policyNumber":"P-1","status":"ACTIVE","covered":true}
            """;
    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wireMock.start();
    }

    private final MockMvc mockMvc;
    private final ClaimWorkerUseCase worker;
    private final PaymentProperties paymentProperties;

    @Autowired
    ClaimFlowEndToEndTest(
            WebApplicationContext context, ClaimWorkerUseCase worker, PaymentProperties paymentProperties) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        this.worker = worker;
        this.paymentProperties = paymentProperties;
    }

    @DynamicPropertySource
    static void outboundSystems(DynamicPropertyRegistry registry) {
        registry.add("claims.client-registry.base-url", wireMock::baseUrl);
        registry.add("claims.policy-manager.base-url", wireMock::baseUrl);
        registry.add("claims.payment.base-url", wireMock::baseUrl);
    }

    @BeforeEach
    void stubKnownClientCoveredPolicyAndPayment() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.get(urlEqualTo("/clients/C-1"))
                .willReturn(okJson("{\"clientId\":\"C-1\",\"status\":\"ACTIVE\"}")));
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/policies/P-1/cover")).willReturn(okJson(COVERED)));
        wireMock.stubFor(WireMock.post(urlEqualTo("/payments")).willReturn(aResponse().withStatus(202)));
    }

    @AfterEach
    void drainRemainingClaims() {
        for (int received = 0; received < 10; received++) {
            if (worker.process() == ClaimWorkerResult.QUEUE_EMPTY) {
                return;
            }
        }
        throw new AssertionError("queue still had a message after 10 receives");
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Test
    void should_mark_an_eligible_claim_paid_after_the_worker_and_a_signed_callback() throws Exception {
        // given
        String claimId = submit(FUNERAL);

        // when
        ClaimWorkerResult result = worker.process();

        // then
        assertThat(result).isEqualTo(ClaimWorkerResult.PAYMENT_INITIATED);
        expectStatus(claimId, "PAYMENT_PENDING");
        wireMock.verify(1, postRequestedFor(urlEqualTo("/payments"))
                .withHeader("Idempotency-Key", equalTo(claimId)));

        // when
        byte[] callback = """
                {"claimId":"%s","outcome":"PAID","idempotencyKey":"%s"}
                """.formatted(claimId, claimId).trim().getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(post("/payments/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PaymentSignatureVerifier.SIGNATURE_HEADER, sign(callback))
                        .content(callback))
                .andExpect(status().isOk());

        // then
        expectStatus(claimId, "PAID");
    }

    @Test
    void should_send_a_death_claim_to_an_analyst_and_start_payment_on_approve() throws Exception {
        // given
        String claimId = submit(DEATH);

        // when
        ClaimWorkerResult result = worker.process();

        // then
        assertThat(result).isEqualTo(ClaimWorkerResult.SENT_TO_ANALYST);
        expectStatus(claimId, "AWAITING_ANALYST");
        wireMock.verify(0, postRequestedFor(urlEqualTo("/payments")));

        // when
        mockMvc.perform(post("/claims/" + claimId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(claimId))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));

        // then
        expectStatus(claimId, "PAYMENT_PENDING");
        wireMock.verify(1, postRequestedFor(urlEqualTo("/payments"))
                .withHeader("Idempotency-Key", equalTo(claimId)));
    }

    @Test
    void should_reject_a_claim_when_the_client_registry_returns_404() throws Exception {
        // given
        wireMock.resetAll();
        wireMock.stubFor(WireMock.get(urlEqualTo("/clients/C-1")).willReturn(aResponse().withStatus(404)));
        String claimId = submit(FUNERAL);

        // when
        ClaimWorkerResult result = worker.process();

        // then
        assertThat(result).isEqualTo(ClaimWorkerResult.REJECTED);
        mockMvc.perform(get("/claims/" + claimId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("UNKNOWN_CLIENT"));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/policies/P-1/cover")));
        wireMock.verify(0, postRequestedFor(urlEqualTo("/payments")));
    }

    @Test
    void should_process_a_death_claim_before_an_earlier_funeral_claim() throws Exception {
        // given
        String funeralId = submit(FUNERAL);
        String deathId = submit(DEATH);

        // when
        ClaimWorkerResult result = worker.process();

        // then
        assertThat(result).isEqualTo(ClaimWorkerResult.SENT_TO_ANALYST);
        expectStatus(deathId, "AWAITING_ANALYST");
        expectStatus(funeralId, "ACCEPTED");
    }

    private String submit(String body) throws Exception {
        MvcResult created = mockMvc.perform(post("/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.claimId");
    }

    private void expectStatus(String claimId, String claimStatus) throws Exception {
        mockMvc.perform(get("/claims/" + claimId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(claimStatus));
    }

    private String sign(byte[] body) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                paymentProperties.callbackSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signed = mac.doFinal(body);
        StringBuilder hex = new StringBuilder(signed.length * 2);
        for (byte value : signed) {
            hex.append("%02x".formatted(value & 0xff));
        }
        return hex.toString();
    }
}
