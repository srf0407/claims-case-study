package com.claimsprocessing.claims.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.claimsprocessing.claims.application.ClaimNotFoundException;
import com.claimsprocessing.claims.application.port.in.GetClaimQuery;
import com.claimsprocessing.claims.application.port.in.GetClaimUseCase;
import com.claimsprocessing.claims.application.port.in.SubmitClaimCommand;
import com.claimsprocessing.claims.application.port.in.SubmitClaimUseCase;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GetClaimControllerTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-01-15T10:00:00Z");
    private static final String CLAIM_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void should_return_200_with_the_claim_body() throws Exception {
        // given
        Claim claim = acceptedClaim();
        String claimId = claim.id().value().toString();
        FakeGetClaimUseCase useCase = new FakeGetClaimUseCase(claim);
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = mockMvc.perform(get("/claims/" + claimId));

        // then
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(claimId))
                .andExpect(jsonPath("$.clientId").value("C-1"))
                .andExpect(jsonPath("$.policyNumber").value("P-1"))
                .andExpect(jsonPath("$.claimType").value("FUNERAL"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.incidentDate").value("2026-03-01"))
                .andExpect(jsonPath("$.dateOfDeath").value(nullValue()))
                .andExpect(jsonPath("$.deathCertificateRef").value(nullValue()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.rejectionReason").value(nullValue()))
                .andExpect(jsonPath("$.events[0].type").value("ACCEPTED"))
                .andExpect(jsonPath("$.events[0].occurredAt").value("2026-01-15T10:00:00Z"))
                .andExpect(jsonPath("$.events[0].from").value(nullValue()))
                .andExpect(jsonPath("$.events[0].to").value(nullValue()))
                .andExpect(jsonPath("$.events[0].reason").value(nullValue()));
        assertThat(useCase.query.claimId()).isEqualTo(claim.id());
    }

    @Test
    void should_return_404_when_the_id_is_unknown() throws Exception {
        // given
        FakeGetClaimUseCase useCase = new FakeGetClaimUseCase(null);
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = mockMvc.perform(get("/claims/" + CLAIM_ID));

        // then
        actions.andExpect(status().isNotFound());
        assertThat(useCase.calls).isEqualTo(1);
    }

    @Test
    void should_return_400_when_the_id_is_not_a_uuid() throws Exception {
        // given
        FakeGetClaimUseCase useCase = new FakeGetClaimUseCase(acceptedClaim());
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = mockMvc.perform(get("/claims/not-a-uuid"));

        // then
        actions.andExpect(status().isBadRequest());
        assertThat(useCase.calls).isZero();
    }

    private static MockMvc mockMvc(GetClaimUseCase useCase) {
        return MockMvcBuilders.standaloneSetup(new ClaimController(
                        unusedSubmit(), new SubmitClaimMapper(), useCase, new GetClaimMapper()))
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
                Clock.fixed(ACCEPTED_AT, ZoneOffset.UTC));
    }

    private static SubmitClaimUseCase unusedSubmit() {
        return new SubmitClaimUseCase() {
            @Override
            public ClaimId submit(SubmitClaimCommand command) {
                throw new UnsupportedOperationException("submit");
            }
        };
    }

    private static final class FakeGetClaimUseCase implements GetClaimUseCase {

        private final Claim claim;
        private int calls;
        private GetClaimQuery query;

        private FakeGetClaimUseCase(Claim claim) {
            this.claim = claim;
        }

        @Override
        public Claim get(GetClaimQuery query) {
            calls++;
            this.query = query;
            if (claim == null) {
                throw new ClaimNotFoundException(query.claimId());
            }
            return claim;
        }
    }
}
