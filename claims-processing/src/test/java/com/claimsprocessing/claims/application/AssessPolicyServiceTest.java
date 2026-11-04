package com.claimsprocessing.claims.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.in.AssessPolicyCommand;
import com.claimsprocessing.claims.application.port.in.AssessPolicyUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.PolicyManagerPort;
import com.claimsprocessing.claims.application.port.out.PolicyManagerUnavailableException;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimAccepted;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimStatusChanged;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.RejectionReason;
import org.junit.jupiter.api.Test;

class AssessPolicyServiceTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-03-01T10:15:30Z");
    private static final Instant ASSESSED_AT = Instant.parse("2026-03-01T12:00:00Z");

    private final Clock clock = Clock.fixed(ASSESSED_AT, ZoneOffset.UTC);
    private final RecordingClaimRepository claims = new RecordingClaimRepository();
    private final FakePolicyManager policies = new FakePolicyManager();
    private final AssessPolicyUseCase assessPolicy = new AssessPolicyService(claims, policies, clock);

    @Test
    void should_reject_the_claim_when_the_policy_is_lapsed() {
        // given
        Claim accepted = acceptedClaim();
        claims.store(accepted);
        policies.willAnswer(PolicyAssessmentResult.LAPSED);

        // when
        PolicyAssessmentResult result = assessPolicy.assess(new AssessPolicyCommand(accepted.id()));

        // then
        assertThat(result).isEqualTo(PolicyAssessmentResult.LAPSED);
        assertThat(policies.assessedPolicyNumber()).isEqualTo("P-1");
        assertThat(policies.assessedClaimType()).isEqualTo(ClaimType.FUNERAL);
        assertThat(claims.saveCount()).isEqualTo(1);
        Claim stored = claims.findById(accepted.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(stored.rejectionReason()).contains(RejectionReason.POLICY_LAPSED);
        assertThat(stored.events()).containsExactly(
                new ClaimAccepted(ACCEPTED_AT),
                new ClaimStatusChanged(
                        ClaimStatus.ACCEPTED,
                        ClaimStatus.REJECTED,
                        Optional.of(RejectionReason.POLICY_LAPSED),
                        ASSESSED_AT));
    }

    @Test
    void should_reject_the_claim_when_there_is_no_cover() {
        // given
        Claim accepted = acceptedClaim();
        claims.store(accepted);
        policies.willAnswer(PolicyAssessmentResult.NO_COVER);

        // when
        PolicyAssessmentResult result = assessPolicy.assess(new AssessPolicyCommand(accepted.id()));

        // then
        assertThat(result).isEqualTo(PolicyAssessmentResult.NO_COVER);
        assertThat(policies.assessedPolicyNumber()).isEqualTo("P-1");
        assertThat(policies.assessedClaimType()).isEqualTo(ClaimType.FUNERAL);
        assertThat(claims.saveCount()).isEqualTo(1);
        Claim stored = claims.findById(accepted.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(stored.rejectionReason()).contains(RejectionReason.NO_COVER);
        assertThat(stored.events()).containsExactly(
                new ClaimAccepted(ACCEPTED_AT),
                new ClaimStatusChanged(
                        ClaimStatus.ACCEPTED,
                        ClaimStatus.REJECTED,
                        Optional.of(RejectionReason.NO_COVER),
                        ASSESSED_AT));
    }

    @Test
    void should_leave_the_claim_accepted_when_cover_is_in_force() {
        // given
        Claim accepted = acceptedClaim();
        claims.store(accepted);
        policies.willAnswer(PolicyAssessmentResult.COVERED);

        // when
        PolicyAssessmentResult result = assessPolicy.assess(new AssessPolicyCommand(accepted.id()));

        // then
        assertThat(result).isEqualTo(PolicyAssessmentResult.COVERED);
        assertThat(policies.assessedPolicyNumber()).isEqualTo("P-1");
        assertThat(policies.assessedClaimType()).isEqualTo(ClaimType.FUNERAL);
        assertThat(claims.saveCount()).isZero();
        Claim stored = claims.findById(accepted.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(stored.rejectionReason()).isEmpty();
        assertThat(stored.events()).containsExactly(new ClaimAccepted(ACCEPTED_AT));
    }

    @Test
    void should_fail_when_the_claim_is_missing() {
        // given
        ClaimId id = new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        // when / then
        assertThatThrownBy(() -> assessPolicy.assess(new AssessPolicyCommand(id)))
                .isInstanceOf(ClaimNotFoundException.class)
                .hasMessageContaining(id.value().toString());
        assertThat(policies.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_fail_when_the_claim_is_not_accepted() {
        // given
        Claim awaitingAnalyst = acceptedClaim().transitionTo(ClaimStatus.AWAITING_ANALYST, clock);
        claims.store(awaitingAnalyst);

        // when / then
        assertThatThrownBy(() -> assessPolicy.assess(new AssessPolicyCommand(awaitingAnalyst.id())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(awaitingAnalyst.id().value().toString())
                .hasMessageContaining(ClaimStatus.AWAITING_ANALYST.name());
        assertThat(policies.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
        assertThat(claims.findById(awaitingAnalyst.id()).orElseThrow().status())
                .isEqualTo(ClaimStatus.AWAITING_ANALYST);
    }

    @Test
    void should_leave_the_claim_unchanged_when_the_policy_manager_is_unavailable() {
        // given
        Claim accepted = acceptedClaim();
        claims.store(accepted);
        policies.willFail(new PolicyManagerUnavailableException("Policy Manager is unavailable"));

        // when / then
        assertThatThrownBy(() -> assessPolicy.assess(new AssessPolicyCommand(accepted.id())))
                .isInstanceOf(PolicyManagerUnavailableException.class);
        assertThat(claims.saveCount()).isZero();
        Claim stored = claims.findById(accepted.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(stored.events()).containsExactly(new ClaimAccepted(ACCEPTED_AT));
    }

    @Test
    void should_reject_a_null_command() {
        // given / when / then
        assertThatThrownBy(() -> assessPolicy.assess(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
        assertThat(policies.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_reject_a_null_claim_id() {
        // given / when / then
        assertThatThrownBy(() -> assessPolicy.assess(new AssessPolicyCommand(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimId");
        assertThat(policies.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
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

    private static final class RecordingClaimRepository implements ClaimRepositoryPort {

        private final Map<ClaimId, Claim> claims = new HashMap<>();
        private int saveCount;

        void store(Claim claim) {
            claims.put(claim.id(), claim);
        }

        int saveCount() {
            return saveCount;
        }

        @Override
        public void save(Claim claim) {
            saveCount++;
            claims.put(claim.id(), claim);
        }

        @Override
        public Optional<Claim> findById(ClaimId id) {
            return Optional.ofNullable(claims.get(id));
        }

        @Override
        public List<Claim> findOpenDeathClaims() {
            return claims.values().stream()
                    .filter(claim -> claim.claimType() == ClaimType.DEATH)
                    .filter(claim -> claim.status() == ClaimStatus.ACCEPTED
                            || claim.status() == ClaimStatus.AWAITING_ANALYST)
                    .toList();
        }
    }

    private static final class FakePolicyManager implements PolicyManagerPort {

        private PolicyAssessmentResult result = PolicyAssessmentResult.COVERED;
        private RuntimeException failure;
        private int callCount;
        private String policyNumber;
        private ClaimType claimType;

        void willAnswer(PolicyAssessmentResult result) {
            this.result = result;
            this.failure = null;
        }

        void willFail(RuntimeException failure) {
            this.failure = failure;
        }

        int callCount() {
            return callCount;
        }

        String assessedPolicyNumber() {
            return policyNumber;
        }

        ClaimType assessedClaimType() {
            return claimType;
        }

        @Override
        public PolicyAssessmentResult assess(String policyNumber, ClaimType claimType) {
            callCount++;
            this.policyNumber = policyNumber;
            this.claimType = claimType;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
