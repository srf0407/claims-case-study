package com.claimsprocessing.claims.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
        expectInvalidSignature(actions);
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
        expectInvalidSignature(actions);
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
        expectInvalidSignature(actions);
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_400_when_the_claim_id_is_missing() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED);
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = """
                {"outcome":"PAID","idempotencyKey":"%s"}
                """.formatted(CLAIM_ID).trim().getBytes(StandardCharsets.UTF_8);

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        expectInvalidCallback(actions, "claimId is required");
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_400_when_the_outcome_is_missing() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED);
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = """
                {"claimId":"%s","idempotencyKey":"%s"}
                """.formatted(CLAIM_ID, CLAIM_ID).trim().getBytes(StandardCharsets.UTF_8);

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        expectInvalidCallback(actions, "outcome is required");
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_400_when_the_idempotency_key_is_missing() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED);
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = """
                {"claimId":"%s","outcome":"PAID"}
                """.formatted(CLAIM_ID).trim().getBytes(StandardCharsets.UTF_8);

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        expectInvalidCallback(actions, "idempotencyKey is required");
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_400_when_the_outcome_is_unknown() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED);
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = """
                {"claimId":"%s","outcome":"REFUNDED","idempotencyKey":"%s"}
                """.formatted(CLAIM_ID, CLAIM_ID).trim().getBytes(StandardCharsets.UTF_8);

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        expectUnreadableCallback(actions);
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
        expectUnreadableCallback(actions);
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_400_when_the_idempotency_key_does_not_match() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(
                new IllegalArgumentException("idempotencyKey mismatch"));
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = callbackBody(CLAIM_ID, "other-key");

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        expectInvalidCallback(actions, "idempotencyKey does not match the claim");
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
        expectInvalidCallback(actions, "claimId must be a UUID");
        assertThat(actions.andReturn().getResponse().getContentAsString()).doesNotContain("Invalid UUID string");
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_400_when_the_callback_argument_is_unrecognised() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(new IllegalArgumentException("secret-detail"));
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = callbackBody(CLAIM_ID, CLAIM_ID);

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        expectInvalidCallback(actions, "Request body could not be read");
        assertThat(actions.andReturn().getResponse().getContentAsString()).doesNotContain("secret-detail");
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
        expectProblem(
                actions,
                409,
                "/problems/payment-conflict",
                "Payment conflict",
                "Claim is not awaiting payment",
                "/payments/callback");
        assertThat(actions.andReturn().getResponse().getContentAsString()).doesNotContain("ACCEPTED");
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
        expectProblem(
                actions,
                404,
                "/problems/claim-not-found",
                "Claim not found",
                "Claim not found",
                "/payments/callback");
    }

    @Test
    void should_not_turn_a_get_illegal_state_into_409() throws Exception {
        // given
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

        // when
        ResultActions actions = mockMvc.perform(get("/claims/" + CLAIM_ID));

        // then
        expectProblem(
                actions,
                500,
                "/problems/internal-error",
                "Internal error",
                "An unexpected error occurred",
                "/claims/" + CLAIM_ID);
        assertThat(actions.andReturn().getResponse().getContentAsString())
                .doesNotContain("ACCEPTED")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void should_return_404_for_an_unknown_path() throws Exception {
        // given
        MockMvc mockMvc = bothControllers();
        ListAppender<ILoggingEvent> logs = attachHandlerLog();

        // when
        ResultActions actions;
        try {
            actions = mockMvc.perform(get("/nope"));
        } finally {
            detachHandlerLog(logs);
        }

        // then
        expectProblem(
                actions,
                404,
                "/problems/request-error",
                "Not Found",
                "Request could not be handled",
                "/nope");
        assertThat(actions.andReturn().getResponse().getContentAsString()).doesNotContain("NoResourceFoundException");
        assertThat(logs.list).extracting(ILoggingEvent::getLevel).doesNotContain(Level.ERROR);
    }

    @Test
    void should_return_405_for_a_wrong_method() throws Exception {
        // given
        MockMvc mockMvc = bothControllers();
        ListAppender<ILoggingEvent> logs = attachHandlerLog();

        // when
        ResultActions actions;
        try {
            actions = mockMvc.perform(delete("/claims"));
        } finally {
            detachHandlerLog(logs);
        }

        // then
        expectProblem(
                actions,
                405,
                "/problems/request-error",
                "Method Not Allowed",
                "Request could not be handled",
                "/claims");
        assertThat(actions.andReturn().getResponse().getContentAsString())
                .doesNotContain("HttpRequestMethodNotSupportedException");
        assertThat(logs.list).extracting(ILoggingEvent::getLevel).doesNotContain(Level.ERROR);
    }

    @Test
    void should_return_500_when_the_callback_fails_unexpectedly() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(new RuntimeException("secret-marker"));
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = callbackBody(CLAIM_ID, CLAIM_ID);

        // when
        ResultActions actions = performCallback(mockMvc, body, sign(body));

        // then
        expectProblem(
                actions,
                500,
                "/problems/internal-error",
                "Internal error",
                "An unexpected error occurred",
                "/payments/callback");
        assertThat(actions.andReturn().getResponse().getContentAsString())
                .doesNotContain("secret-marker")
                .doesNotContain("RuntimeException");
    }

    @Test
    void should_accept_a_callback_that_includes_an_unknown_json_property() throws Exception {
        // given
        FakeApplyPaymentStatus useCase = new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED);
        MockMvc mockMvc = callbackMvc(useCase);
        byte[] body = """
                {"claimId":"%s","outcome":"PAID","idempotencyKey":"%s","note":"ignore-me"}
                """.formatted(CLAIM_ID, CLAIM_ID).trim().getBytes(StandardCharsets.UTF_8);

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

    private static void expectInvalidSignature(ResultActions actions) throws Exception {
        expectProblem(
                actions,
                401,
                "/problems/invalid-signature",
                "Invalid signature",
                "Payment signature is invalid",
                "/payments/callback");
    }

    private static void expectInvalidCallback(ResultActions actions, String detail) throws Exception {
        expectProblem(
                actions,
                400,
                "/problems/invalid-callback",
                "Invalid callback",
                detail,
                "/payments/callback");
    }

    private static void expectUnreadableCallback(ResultActions actions) throws Exception {
        expectInvalidCallback(actions, "Request body could not be read");
        assertThat(actions.andReturn().getResponse().getContentAsString()).doesNotContain("Jackson");
    }

    private static void expectProblem(
            ResultActions actions, int status, String type, String title, String detail, String instance)
            throws Exception {
        actions.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(type))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.instance").value(instance));
    }

    private MockMvc bothControllers() {
        return MockMvcBuilders.standaloneSetup(
                        new ClaimController(
                                unusedSubmit(), new SubmitClaimMapper(), query -> {
                                    throw new UnsupportedOperationException("get");
                                }, new GetClaimMapper()),
                        callbackController(new FakeApplyPaymentStatus(PaymentStatusResult.APPLIED)))
                .setControllerAdvice(new ClaimExceptionHandler(), new PaymentCallbackExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private static ListAppender<ILoggingEvent> attachHandlerLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(ClaimExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachHandlerLog(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(ClaimExceptionHandler.class);
        logger.detachAppender(appender);
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
