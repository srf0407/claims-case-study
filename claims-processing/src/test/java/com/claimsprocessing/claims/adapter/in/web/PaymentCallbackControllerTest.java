package com.claimsprocessing.claims.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.claimsprocessing.claims.application.ClaimNotFoundException;
import com.claimsprocessing.claims.application.PaymentStatusResult;
import com.claimsprocessing.claims.application.port.in.ApplyPaymentStatusCommand;
import com.claimsprocessing.claims.application.port.in.ApplyPaymentStatusUseCase;
import com.claimsprocessing.claims.application.port.in.GetClaimUseCase;
import com.claimsprocessing.claims.application.port.in.SubmitClaimCommand;
import com.claimsprocessing.claims.application.port.in.SubmitClaimUseCase;
import com.claimsprocessing.claims.domain.ClaimId;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class PaymentCallbackControllerTest {

    private static final String SECRET = "test-secret";
    private static final String CLAIM_ID = "11111111-1111-1111-1111-111111111111";

    private final PaymentCallbackMapper mapper = new PaymentCallbackMapper(JsonMapper.builder().build());

    @Test
    void should_return_200_when_the_payment_is_applied() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED);
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = callbackBody(CLAIM_ID, CLAIM_ID);

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("APPLIED"));
        assertThat(useCase.command).isEqualTo(new ApplyPaymentStatusCommand(
                new ClaimId(UUID.fromString(CLAIM_ID)),
                com.claimsprocessing.claims.application.port.in.PaymentOutcome.PAID,
                CLAIM_ID));
    }

    @Test
    void should_return_200_when_the_callback_is_a_duplicate() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(PaymentStatusResult.DUPLICATE);
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = callbackBody(CLAIM_ID, CLAIM_ID);

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DUPLICATE"));
    }

    @Test
    void should_return_401_when_the_signature_header_is_missing() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED);
        MockMvc mockMvc = callbackMvc(useCase);

        // when
        ResultActions actions = performCallback(mockMvc, callbackBody(CLAIM_ID, CLAIM_ID), null);

        // then
        actions.andExpect(status().isUnauthorized());
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_401_when_the_signature_is_wrong() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED);
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = callbackBody(CLAIM_ID, CLAIM_ID);

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body, "other-secret"));

        // then
        actions.andExpect(status().isUnauthorized());
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_401_when_a_non_json_body_is_not_signed() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED);
        MockMvc mockMvc = callbackMvc(useCase);

        // when
        ResultActions actions = performCallback(mockMvc, "{".getBytes(StandardCharsets.UTF_8), null);

        // then
        actions.andExpect(status().isUnauthorized());
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_400_when_a_signed_body_is_not_json() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED);
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = "{".getBytes(StandardCharsets.UTF_8);

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        actions.andExpect(status().isBadRequest());
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_400_when_the_idempotency_key_does_not_match() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(new IllegalArgumentException("idempotencyKey"));
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = callbackBody(CLAIM_ID, "other-key");

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        actions.andExpect(status().isBadRequest());
    }

    @Test
    void should_return_400_when_the_claim_id_is_not_a_uuid() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED);
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = callbackBody("not-a-uuid", "not-a-uuid");

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        actions.andExpect(status().isBadRequest());
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_409_when_the_claim_is_not_awaiting_payment() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(new IllegalStateException("Claim is ACCEPTED"));
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = callbackBody(CLAIM_ID, CLAIM_ID);

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        actions.andExpect(status().isConflict());
    }

    @Test
    void should_return_404_when_the_claim_is_unknown() throws Exception {
        // given
        ClaimId id = new ClaimId(UUID.fromString(CLAIM_ID));
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(new ClaimNotFoundException(id));
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = callbackBody(CLAIM_ID, CLAIM_ID);

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        actions.andExpect(status().isNotFound());
    }

    @Test
    void should_not_turn_a_get_illegal_state_into_409() {
        GetClaimUseCase getClaim = query -> {
            throw new IllegalStateException("Claim is ACCEPTED");
        };
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new ClaimController(
                                unusedSubmit(), new SubmitClaimMapper(), getClaim, new GetClaimMapper()),
                        callbackController(new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED)))
                .setControllerAdvice(new ClaimExceptionHandler(), new PaymentCallbackExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();

        assertThatThrownBy(() -> mockMvc.perform(get("/claims/" + CLAIM_ID)))
                .isInstanceOf(ServletException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class);
    }

    private MockMvc callbackMvc(ApplyPaymentStatusUseCase useCase) {
        return MockMvcBuilders.standaloneSetup(callbackController(useCase))
                .setControllerAdvice(new ClaimExceptionHandler(), new PaymentCallbackExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private PaymentCallbackController callbackController(ApplyPaymentStatusUseCase useCase) {
        return new PaymentCallbackController(new PaymentSignatureVerifier(SECRET), mapper, useCase);
    }

    private static ResultActions performCallback(MockMvc mockMvc, byte[] body, String signature) throws Exception {
        MockHttpServletRequestBuilder request = post("/payments/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (signature != null) {
            request.header(PaymentSignatureVerifier.SIGNATURE_HEADER, signature);
        }
        return mockMvc.perform(request);
    }

    private static byte[] callbackBody(String claimId, String idempotencyKey) {
        return """
                {"claimId":"%s","outcome":"PAID","idempotencyKey":"%s"}
                """.formatted(claimId, idempotencyKey).trim().getBytes(StandardCharsets.UTF_8);
    }

    private static String sign(byte[] body) throws GeneralSecurityException {
        return sign(body, SECRET);
    }

    private static String sign(byte[] body, String secret) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signed = mac.doFinal(body);
        StringBuilder hex = new StringBuilder(signed.length * 2);
        for (byte value : signed) {
            hex.append("%02x".formatted(value & 0xff));
        }
        return hex.toString();
    }

    private static SubmitClaimUseCase unusedSubmit() {
        return new SubmitClaimUseCase() {
            @Override
            public ClaimId submit(SubmitClaimCommand command) {
                throw new UnsupportedOperationException("submit");
            }
        };
    }

    private static final class FakeApplyPaymentStatus implements ApplyPaymentStatusUseCase {

        private final PaymentStatusResult result;
        private final RuntimeException failure;
        private int calls;
        private ApplyPaymentStatusCommand command;

        private FakeApplyPaymentStatus(PaymentStatusResult result) {
            this.result = result;
            this.failure = null;
        }

        private FakeApplyPaymentStatus(RuntimeException failure) {
            this.result = null;
            this.failure = failure;
        }

        @Override
        public PaymentStatusResult apply(ApplyPaymentStatusCommand command) {
            calls++;
            this.command = command;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
