package com.claimsprocessing.claims.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.claimsprocessing.claims.application.ClaimNotAwaitingAnalystException;
import com.claimsprocessing.claims.application.ClaimNotFoundException;
import com.claimsprocessing.claims.application.port.in.ClaimDecision;
import com.claimsprocessing.claims.application.port.in.DecideClaimCommand;
import com.claimsprocessing.claims.application.port.in.DecideClaimUseCase;
import com.claimsprocessing.claims.application.port.in.GetClaimQuery;
import com.claimsprocessing.claims.application.port.in.GetClaimUseCase;
import com.claimsprocessing.claims.application.port.in.SubmitClaimCommand;
import com.claimsprocessing.claims.application.port.in.SubmitClaimUseCase;
import com.claimsprocessing.claims.application.port.out.PaymentUnavailableException;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimAccepted;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.RejectionReason;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DecideClaimControllerTest {

    private static final String CLAIM_ID = "11111111-1111-1111-1111-111111111111";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void should_return_200_when_the_analyst_approves() throws Exception {
        // given
        Claim pending = acceptedClaim().transitionTo(ClaimStatus.PAYMENT_PENDING, CLOCK);
        FakeDecideClaimUseCase useCase = new FakeDecideClaimUseCase(pending, null);
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = mockMvc.perform(post("/claims/" + CLAIM_ID + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}"));

        // then
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(pending.id().value().toString()))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));
        assertThat(useCase.calls).isEqualTo(1);
        assertThat(useCase.command).isEqualTo(
                new DecideClaimCommand(new ClaimId(UUID.fromString(CLAIM_ID)), ClaimDecision.APPROVE));
    }

    @Test
    void should_return_200_when_the_analyst_rejects() throws Exception {
        // given
        Claim rejected = acceptedClaim()
                .transitionTo(ClaimStatus.AWAITING_ANALYST, CLOCK)
                .reject(RejectionReason.ANALYST_REJECTED, CLOCK);
        FakeDecideClaimUseCase useCase = new FakeDecideClaimUseCase(rejected, null);
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = mockMvc.perform(post("/claims/" + CLAIM_ID + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}"));

        // then
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(rejected.id().value().toString()))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(useCase.command).isEqualTo(
                new DecideClaimCommand(new ClaimId(UUID.fromString(CLAIM_ID)), ClaimDecision.REJECT));
    }

    @Test
    void should_return_the_use_case_result() throws Exception {
        // given
        Claim pending = Claim.restore(
                new ClaimId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                "C-1",
                "P-1",
                ClaimType.FUNERAL,
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 1),
                null,
                null,
                ClaimStatus.PAYMENT_PENDING,
                Optional.empty(),
                List.of(new ClaimAccepted(CLOCK.instant())));
        FakeDecideClaimUseCase useCase = new FakeDecideClaimUseCase(pending, null);
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = mockMvc.perform(post("/claims/" + CLAIM_ID + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}"));

        // then
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(pending.id().value().toString()))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));
        assertThat(pending.id().value()).isNotEqualTo(UUID.fromString(CLAIM_ID));
        assertThat(useCase.command.decision()).isEqualTo(ClaimDecision.REJECT);
    }

    @Test
    void should_return_404_when_the_id_is_unknown() throws Exception {
        // given
        var failure = new ClaimNotFoundException(new ClaimId(UUID.fromString(CLAIM_ID)));
        FakeDecideClaimUseCase useCase = new FakeDecideClaimUseCase(null, failure);
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = postDecision(mockMvc, CLAIM_ID, "{\"decision\":\"APPROVE\"}");

        // then
        expectProblem(
                actions,
                404,
                "/problems/claim-not-found",
                "Claim not found",
                "Claim not found",
                "/claims/" + CLAIM_ID + "/decision");
        assertThat(useCase.calls).isEqualTo(1);
    }

    @Test
    void should_return_409_when_the_claim_is_not_awaiting_an_analyst() throws Exception {
        // given
        FakeDecideClaimUseCase useCase = new FakeDecideClaimUseCase(null, new ClaimNotAwaitingAnalystException());
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = postDecision(mockMvc, CLAIM_ID, "{\"decision\":\"APPROVE\"}");

        // then
        expectProblem(
                actions,
                409,
                "/problems/claim-not-awaiting-analyst",
                "Claim is not awaiting an analyst",
                "Claim is not awaiting an analyst",
                "/claims/" + CLAIM_ID + "/decision");
    }

    @Test
    void should_return_400_when_the_json_is_malformed() throws Exception {
        // given
        FakeDecideClaimUseCase useCase = new FakeDecideClaimUseCase(null, null);
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = postDecision(mockMvc, CLAIM_ID, "{");

        // then
        expectUnreadable(actions, CLAIM_ID);
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_400_when_the_decision_is_unknown() throws Exception {
        // given
        FakeDecideClaimUseCase useCase = new FakeDecideClaimUseCase(null, null);
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = postDecision(mockMvc, CLAIM_ID, "{\"decision\":\"approve\"}");

        // then
        expectUnreadable(actions, CLAIM_ID);
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_400_when_the_decision_is_missing() throws Exception {
        // given
        FakeDecideClaimUseCase useCase = new FakeDecideClaimUseCase(null, null);
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = postDecision(mockMvc, CLAIM_ID, "{}");

        // then
        expectUnreadable(actions, CLAIM_ID);
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_400_when_the_id_is_not_a_uuid() throws Exception {
        // given
        FakeDecideClaimUseCase useCase = new FakeDecideClaimUseCase(null, null);
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = postDecision(mockMvc, "not-a-uuid", "{\"decision\":\"APPROVE\"}");

        // then
        expectProblem(
                actions,
                400,
                "/problems/malformed-request",
                "Malformed request",
                "id must be a UUID",
                "/claims/not-a-uuid/decision");
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_500_when_payment_is_unavailable() throws Exception {
        // given
        FakeDecideClaimUseCase useCase = new FakeDecideClaimUseCase(
                null, new PaymentUnavailableException("Payment System is unavailable"));
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = postDecision(mockMvc, CLAIM_ID, "{\"decision\":\"APPROVE\"}");

        // then
        expectProblem(
                actions,
                500,
                "/problems/internal-error",
                "Internal error",
                "An unexpected error occurred",
                "/claims/" + CLAIM_ID + "/decision");
        assertThat(useCase.calls).isEqualTo(1);
    }

    private static ResultActions postDecision(MockMvc mockMvc, String id, String body) throws Exception {
        return mockMvc.perform(post("/claims/" + id + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static void expectUnreadable(ResultActions actions, String id) throws Exception {
        expectProblem(
                actions,
                400,
                "/problems/malformed-request",
                "Malformed request",
                "Request body could not be read",
                "/claims/" + id + "/decision");
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

    private static MockMvc mockMvc(DecideClaimUseCase useCase) {
        return MockMvcBuilders.standaloneSetup(new ClaimController(
                        unusedSubmit(),
                        new SubmitClaimMapper(),
                        unusedGet(),
                        new GetClaimMapper(),
                        useCase,
                        new DecideClaimMapper()))
                .setControllerAdvice(new ClaimExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private static Claim acceptedClaim() {
        return Claim.accept(
                ClaimType.FUNERAL,
                "C-1",
                "P-1",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 1),
                null,
                null,
                CLOCK);
    }

    private static SubmitClaimUseCase unusedSubmit() {
        return command -> {
            throw new UnsupportedOperationException("submit");
        };
    }

    private static GetClaimUseCase unusedGet() {
        return new GetClaimUseCase() {
            @Override
            public Claim get(GetClaimQuery query) {
                throw new UnsupportedOperationException("get");
            }
        };
    }

    private static final class FakeDecideClaimUseCase implements DecideClaimUseCase {

        private final Claim result;
        private final RuntimeException failure;
        private int calls;
        private DecideClaimCommand command;

        private FakeDecideClaimUseCase(Claim result, RuntimeException failure) {
            this.result = result;
            this.failure = failure;
        }

        @Override
        public Claim decide(DecideClaimCommand command) {
            calls++;
            this.command = command;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
