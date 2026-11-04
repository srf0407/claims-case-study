package com.claimsprocessing.claims;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.claimsprocessing.claims.adapter.in.web.PaymentSignatureVerifier;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.config.PaymentProperties;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "claims.death-sla.check-interval=PT24H")
class PaymentCallbackEndToEndTest {

    private final WebApplicationContext context;
    private final ClaimRepositoryPort claims;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    @Autowired
    PaymentCallbackEndToEndTest(
            WebApplicationContext context,
            ClaimRepositoryPort claims,
            PaymentProperties paymentProperties,
            Clock clock) {
        this.context = context;
        this.claims = claims;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
    }

    @Test
    void should_mark_the_claim_paid_after_a_signed_callback() throws Exception {
        // given
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String submitted = """
                {"claimType":"FUNERAL","clientId":"C-1","policyNumber":"P-1","amount":150.00,"incidentDate":"2026-03-01"}
                """;
        MvcResult created = mockMvc.perform(post("/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitted))
                .andExpect(status().isCreated())
                .andReturn();
        String claimId = JsonPath.read(created.getResponse().getContentAsString(), "$.claimId");
        Claim stored = claims.findById(new ClaimId(UUID.fromString(claimId))).orElseThrow();
        claims.save(stored.transitionTo(ClaimStatus.PAYMENT_PENDING, clock));
        byte[] callback = """
                {"claimId":"%s","outcome":"PAID","idempotencyKey":"%s"}
                """.formatted(claimId, claimId).trim().getBytes(StandardCharsets.UTF_8);

        // when
        mockMvc.perform(post("/payments/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PaymentSignatureVerifier.SIGNATURE_HEADER, sign(callback))
                        .content(callback))
                .andExpect(status().isOk());
        var fetched = mockMvc.perform(get("/claims/" + claimId));

        // then
        fetched.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
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
