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
import java.util.stream.Stream;

import com.claimsprocessing.claims.application.port.in.StraightThroughPaymentCommand;
import com.claimsprocessing.claims.application.port.in.StraightThroughPaymentUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.PaymentPort;
import com.claimsprocessing.claims.application.port.out.PaymentRequest;
import com.claimsprocessing.claims.application.port.out.PaymentUnavailableException;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimAccepted;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimStatusChanged;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.RejectionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;

class StraightThroughPaymentServiceTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-03-01T10:15:30Z");
    private static final Instant INITIATED_AT = Instant.parse("2026-03-01T12:00:00Z");
    private static final BigDecimal LIMIT = new BigDecimal("10000.00");

    private final Clock clock = Clock.fixed(INITIATED_AT, ZoneOffset.UTC);
    private final RecordingClaimRepository claims = new RecordingClaimRepository();
    private final FakePayment payments = new FakePayment();
    private final PaymentRequestMapper paymentRequestMapper = new PaymentRequestMapper();
    private final StraightThroughPaymentUseCase straightThrough =
            new StraightThroughPaymentService(claims, payments, paymentRequestMapper, clock, LIMIT);

    @ParameterizedTest
    @EnumSource(value = ClaimType.class, names = {"DISABILITY", "FUNERAL"})
    void should_initiate_payment_for_an_eligible_accepted_claim(ClaimType claimType) {
        // given
        Claim claim = accepted(claimType, new BigDecimal("9999.99"));
        claims.store(claim);

        // when
        StraightThroughResult result = straightThrough.execute(eligible(claim));

        // then
        assertThat(result).isEqualTo(StraightThroughResult.PAYMENT_INITIATED);
        assertThat(payments.requests()).containsExactly(paymentRequestMapper.map(claim));
        assertThat(claims.saveCount()).isEqualTo(1);
        Claim stored = claims.findById(claim.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ClaimStatus.PAYMENT_PENDING);
        assertThat(stored.rejectionReason()).isEmpty();
        assertThat(stored.events()).containsExactly(
                new ClaimAccepted(ACCEPTED_AT),
                new ClaimStatusChanged(
                        ClaimStatus.ACCEPTED,
                        ClaimStatus.PAYMENT_PENDING,
                        Optional.empty(),
                        INITIATED_AT));
    }

    @ParameterizedTest
    @MethodSource("ineligibleClaims")
    void should_send_an_ineligible_accepted_claim_to_the_analyst(Claim claim, boolean flagged) {
        // given
        claims.store(claim);

        // when
        StraightThroughResult result = straightThrough.execute(new StraightThroughPaymentCommand(
                claim.id(), ClientCheckResult.KNOWN, PolicyAssessmentResult.COVERED, flagged));

        // then
        assertThat(result).isEqualTo(StraightThroughResult.SENT_TO_ANALYST);
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isEqualTo(1);
        Claim stored = claims.findById(claim.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ClaimStatus.AWAITING_ANALYST);
        assertThat(stored.rejectionReason()).isEmpty();
        assertThat(stored.events()).containsExactly(
                new ClaimAccepted(ACCEPTED_AT),
                new ClaimStatusChanged(
                        ClaimStatus.ACCEPTED,
                        ClaimStatus.AWAITING_ANALYST,
                        Optional.empty(),
                        INITIATED_AT));
    }

    @Test
    void should_leave_the_claim_accepted_when_payment_is_unavailable() {
        // given
        Claim claim = accepted(ClaimType.FUNERAL, new BigDecimal("9999.99"));
        claims.store(claim);
        payments.willFail(new PaymentUnavailableException("Payment System is unavailable"));

        // when / then
        assertThatThrownBy(() -> straightThrough.execute(eligible(claim)))
                .isInstanceOf(PaymentUnavailableException.class);
        assertThat(payments.requests()).containsExactly(paymentRequestMapper.map(claim));
        assertThat(claims.saveCount()).isZero();
        Claim stored = claims.findById(claim.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(stored.events()).containsExactly(new ClaimAccepted(ACCEPTED_AT));
    }

    @Test
    void should_send_the_same_idempotency_key_when_payment_is_retried() {
        // given
        Claim claim = accepted(ClaimType.FUNERAL, new BigDecimal("9999.99"));
        claims.store(claim);
        payments.failOnce(new PaymentUnavailableException("Payment System is unavailable"));

        // when
        assertThatThrownBy(() -> straightThrough.execute(eligible(claim)))
                .isInstanceOf(PaymentUnavailableException.class);
        StraightThroughResult result = straightThrough.execute(eligible(claim));

        // then
        PaymentRequest request = paymentRequestMapper.map(claim);
        assertThat(result).isEqualTo(StraightThroughResult.PAYMENT_INITIATED);
        assertThat(payments.requests()).containsExactly(request, request);
        assertThat(claims.saveCount()).isEqualTo(1);
        assertThat(claims.findById(claim.id()).orElseThrow().status()).isEqualTo(ClaimStatus.PAYMENT_PENDING);
    }

    @Test
    void should_fail_when_the_claim_is_missing() {
        // given
        ClaimId id = new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        // when / then
        assertThatThrownBy(() -> straightThrough.execute(new StraightThroughPaymentCommand(
                        id, ClientCheckResult.KNOWN, PolicyAssessmentResult.COVERED, false)))
                .isInstanceOf(ClaimNotFoundException.class)
                .hasMessageContaining(id.value().toString());
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, mode = Mode.EXCLUDE, names = "ACCEPTED")
    void should_refuse_a_claim_that_is_not_accepted(ClaimStatus status) {
        // given
        Claim claim = claimIn(status);
        claims.store(claim);

        // when / then
        assertThatThrownBy(() -> straightThrough.execute(eligible(claim)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Claim " + claim.id().value() + " is " + status.name());
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
        assertThat(claims.findById(claim.id()).orElseThrow().status()).isEqualTo(status);
    }

    @Test
    void should_leave_a_death_claim_accepted_when_the_client_is_not_known() {
        // given
        Claim claim = accepted(ClaimType.DEATH, new BigDecimal("9999.99"));
        claims.store(claim);

        // when / then
        assertThatThrownBy(() -> straightThrough.execute(new StraightThroughPaymentCommand(
                        claim.id(), ClientCheckResult.UNKNOWN, PolicyAssessmentResult.COVERED, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Claim " + claim.id().value() + " client check is UNKNOWN");
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
        assertThat(claims.findById(claim.id()).orElseThrow().status()).isEqualTo(ClaimStatus.ACCEPTED);
    }

    @ParameterizedTest
    @EnumSource(value = PolicyAssessmentResult.class, names = {"LAPSED", "NO_COVER"})
    void should_refuse_when_the_policy_is_not_covered(PolicyAssessmentResult assessment) {
        // given
        Claim claim = accepted(ClaimType.FUNERAL, new BigDecimal("9999.99"));
        claims.store(claim);

        // when / then
        assertThatThrownBy(() -> straightThrough.execute(new StraightThroughPaymentCommand(
                        claim.id(), ClientCheckResult.KNOWN, assessment, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Claim " + claim.id().value() + " policy assessment is " + assessment.name());
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
        assertThat(claims.findById(claim.id()).orElseThrow().status()).isEqualTo(ClaimStatus.ACCEPTED);
    }

    @Test
    void should_report_the_client_check_when_both_checks_fail() {
        // given
        Claim claim = accepted(ClaimType.FUNERAL, new BigDecimal("9999.99"));
        claims.store(claim);

        // when / then
        assertThatThrownBy(() -> straightThrough.execute(new StraightThroughPaymentCommand(
                        claim.id(), ClientCheckResult.UNKNOWN, PolicyAssessmentResult.LAPSED, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Claim " + claim.id().value() + " client check is UNKNOWN");
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_apply_the_limit_supplied_to_the_service() {
        // given
        StraightThroughPaymentUseCase tighterLimit = new StraightThroughPaymentService(
                claims, payments, paymentRequestMapper, clock, new BigDecimal("10.00"));
        Claim claim = accepted(ClaimType.FUNERAL, new BigDecimal("10.50"));
        claims.store(claim);

        // when
        StraightThroughResult result = tighterLimit.execute(eligible(claim));

        // then
        assertThat(result).isEqualTo(StraightThroughResult.SENT_TO_ANALYST);
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isEqualTo(1);
        assertThat(claims.findById(claim.id()).orElseThrow().status()).isEqualTo(ClaimStatus.AWAITING_ANALYST);
    }

    @Test
    void should_reject_a_null_command() {
        // given / when / then
        assertThatThrownBy(() -> straightThrough.execute(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_reject_a_null_claim_id() {
        // given / when / then
        assertThatThrownBy(() -> straightThrough.execute(new StraightThroughPaymentCommand(
                        null, ClientCheckResult.KNOWN, PolicyAssessmentResult.COVERED, false)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimId");
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_reject_a_null_client_check() {
        // given / when / then
        assertThatThrownBy(() -> straightThrough.execute(new StraightThroughPaymentCommand(
                        new ClaimId(UUID.randomUUID()), null, PolicyAssessmentResult.COVERED, false)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clientCheck");
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_reject_a_null_policy_assessment() {
        // given / when / then
        assertThatThrownBy(() -> straightThrough.execute(new StraightThroughPaymentCommand(
                        new ClaimId(UUID.randomUUID()), ClientCheckResult.KNOWN, null, false)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("policyAssessment");
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_reject_a_null_limit() {
        // given / when / then
        assertThatThrownBy(() -> new StraightThroughPaymentService(
                        claims, payments, paymentRequestMapper, clock, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("limit");
    }

    private static Stream<Arguments> ineligibleClaims() {
        return Stream.of(
                Arguments.of(accepted(ClaimType.DEATH, new BigDecimal("9999.99")), false),
                Arguments.of(accepted(ClaimType.FUNERAL, new BigDecimal("9999.99")), true),
                Arguments.of(accepted(ClaimType.FUNERAL, new BigDecimal("10000.0")), false),
                Arguments.of(accepted(ClaimType.FUNERAL, new BigDecimal("10000.01")), false));
    }

    private static StraightThroughPaymentCommand eligible(Claim claim) {
        return new StraightThroughPaymentCommand(
                claim.id(), ClientCheckResult.KNOWN, PolicyAssessmentResult.COVERED, false);
    }

    private static Claim accepted(ClaimType claimType, BigDecimal amount) {
        if (claimType == ClaimType.DEATH) {
            return Claim.accept(
                    claimType,
                    "C-1",
                    "P-1",
                    amount,
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 2, 20),
                    "CERT-1",
                    Clock.fixed(ACCEPTED_AT, ZoneOffset.UTC));
        }
        return Claim.accept(
                claimType,
                "C-1",
                "P-1",
                amount,
                LocalDate.of(2026, 3, 1),
                null,
                null,
                Clock.fixed(ACCEPTED_AT, ZoneOffset.UTC));
    }

    private Claim claimIn(ClaimStatus status) {
        Claim accepted = accepted(ClaimType.FUNERAL, new BigDecimal("9999.99"));
        return switch (status) {
            case ACCEPTED -> accepted;
            case AWAITING_ANALYST -> accepted.transitionTo(ClaimStatus.AWAITING_ANALYST, clock);
            case APPROVED -> accepted.transitionTo(ClaimStatus.AWAITING_ANALYST, clock)
                    .transitionTo(ClaimStatus.APPROVED, clock);
            case PAYMENT_PENDING -> accepted.transitionTo(ClaimStatus.PAYMENT_PENDING, clock);
            case PAID -> accepted.transitionTo(ClaimStatus.PAYMENT_PENDING, clock)
                    .transitionTo(ClaimStatus.PAID, clock);
            case PAYMENT_FAILED -> accepted.transitionTo(ClaimStatus.PAYMENT_PENDING, clock)
                    .transitionTo(ClaimStatus.PAYMENT_FAILED, clock);
            case REJECTED -> accepted.reject(RejectionReason.UNKNOWN_CLIENT, clock);
        };
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
    }

    private static final class FakePayment implements PaymentPort {

        private final List<PaymentRequest> requests = new ArrayList<>();
        private RuntimeException failure;
        private int failuresLeft;

        void willFail(RuntimeException failure) {
            this.failure = failure;
            this.failuresLeft = Integer.MAX_VALUE;
        }

        void failOnce(RuntimeException failure) {
            this.failure = failure;
            this.failuresLeft = 1;
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
            if (failuresLeft > 0) {
                failuresLeft--;
                throw failure;
            }
        }
    }
}
