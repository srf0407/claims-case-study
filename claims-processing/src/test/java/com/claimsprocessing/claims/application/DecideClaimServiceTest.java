package com.claimsprocessing.claims.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.in.ClaimDecision;
import com.claimsprocessing.claims.application.port.in.DecideClaimCommand;
import com.claimsprocessing.claims.application.port.in.DecideClaimUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.PaymentPort;
import com.claimsprocessing.claims.application.port.out.PaymentRequest;
import com.claimsprocessing.claims.application.port.out.PaymentUnavailableException;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.RejectionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

class DecideClaimServiceTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-03-01T10:15:30Z");
    private static final BigDecimal AMOUNT = new BigDecimal("150.00");
    private static final ClaimId CLAIM_ID =
            new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);
    private final RecordingClaimRepository claims = new RecordingClaimRepository();
    private final FakePayment payments = new FakePayment();
    private final DecideClaimUseCase decideClaim = new DecideClaimService(
            claims, new InitiatePaymentService(claims, payments, new PaymentRequestMapper(), clock), clock);

    @Test
    void should_approve_an_awaiting_claim_and_start_payment() {
        // given
        Claim claim = awaitingAnalyst();
        claims.store(claim);

        // when
        Claim result = decideClaim.decide(new DecideClaimCommand(claim.id(), ClaimDecision.APPROVE));

        // then
        assertThat(result.status()).isEqualTo(ClaimStatus.PAYMENT_PENDING);
        assertThat(claims.findById(claim.id()).orElseThrow().status()).isEqualTo(ClaimStatus.PAYMENT_PENDING);
        assertThat(payments.requests()).containsExactly(new PaymentRequest(
                claim.id().value().toString(), claim.id(), "C-1", "P-1", AMOUNT));
    }

    @Test
    void should_reject_an_awaiting_claim() {
        // given
        Claim claim = awaitingAnalyst();
        claims.store(claim);

        // when
        Claim result = decideClaim.decide(new DecideClaimCommand(claim.id(), ClaimDecision.REJECT));

        // then
        assertThat(result.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(result.rejectionReason()).contains(RejectionReason.ANALYST_REJECTED);
        assertThat(claims.findById(claim.id()).orElseThrow().status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(payments.callCount()).isZero();
    }

    @Test
    void should_fail_when_the_claim_is_missing() {
        // given
        ClaimId id = new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        // when / then
        assertThatThrownBy(() -> decideClaim.decide(new DecideClaimCommand(id, ClaimDecision.APPROVE)))
                .isInstanceOf(ClaimNotFoundException.class);
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, mode = Mode.EXCLUDE, names = "AWAITING_ANALYST")
    void should_refuse_when_the_claim_is_not_awaiting_an_analyst(ClaimStatus status) {
        // given
        Claim claim = claimIn(status);
        claims.store(claim);

        // when / then
        assertThatThrownBy(() -> decideClaim.decide(new DecideClaimCommand(claim.id(), ClaimDecision.APPROVE)))
                .isInstanceOf(ClaimNotAwaitingAnalystException.class);
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
        assertThat(claims.findById(claim.id()).orElseThrow().status()).isEqualTo(status);
    }

    @Test
    void should_leave_the_claim_approved_when_payment_is_unavailable() {
        // given
        Claim claim = awaitingAnalyst();
        claims.store(claim);
        payments.willFail(new PaymentUnavailableException("Payment System is unavailable"));

        // when / then
        assertThatThrownBy(() -> decideClaim.decide(new DecideClaimCommand(claim.id(), ClaimDecision.APPROVE)))
                .isInstanceOf(PaymentUnavailableException.class);
        assertThat(payments.callCount()).isEqualTo(1);
        assertThat(claims.findById(claim.id()).orElseThrow().status()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void should_reject_a_null_command_claim_id_or_decision() {
        // given
        // when / then
        assertThatThrownBy(() -> decideClaim.decide(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
        assertThatThrownBy(() -> decideClaim.decide(new DecideClaimCommand(null, ClaimDecision.APPROVE)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimId");
        assertThatThrownBy(() -> decideClaim.decide(new DecideClaimCommand(CLAIM_ID, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("decision");
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    private Claim awaitingAnalyst() {
        return acceptedClaim().transitionTo(ClaimStatus.AWAITING_ANALYST, clock);
    }

    private Claim claimIn(ClaimStatus status) {
        Claim accepted = acceptedClaim();
        return switch (status) {
            case ACCEPTED -> accepted;
            case AWAITING_ANALYST -> awaitingAnalyst();
            case APPROVED -> awaitingAnalyst().transitionTo(ClaimStatus.APPROVED, clock);
            case PAYMENT_PENDING -> accepted.transitionTo(ClaimStatus.PAYMENT_PENDING, clock);
            case PAID -> accepted.transitionTo(ClaimStatus.PAYMENT_PENDING, clock)
                    .transitionTo(ClaimStatus.PAID, clock);
            case PAYMENT_FAILED -> accepted.transitionTo(ClaimStatus.PAYMENT_PENDING, clock)
                    .transitionTo(ClaimStatus.PAYMENT_FAILED, clock);
            case REJECTED -> accepted.reject(RejectionReason.UNKNOWN_CLIENT, clock);
        };
    }

    private static Claim acceptedClaim() {
        return Claim.accept(
                ClaimType.FUNERAL,
                "C-1",
                "P-1",
                AMOUNT,
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
            return List.of();
        }
    }

    private static final class FakePayment implements PaymentPort {

        private final List<PaymentRequest> requests = new ArrayList<>();
        private RuntimeException failure;

        void willFail(RuntimeException failure) {
            this.failure = failure;
        }

        List<PaymentRequest> requests() {
            return List.copyOf(requests);
        }

        int callCount() {
            return requests.size();
        }

        @Override
        public void initiate(PaymentRequest request) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
        }
    }
}
