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

import com.claimsprocessing.claims.application.port.in.InitiatePaymentCommand;
import com.claimsprocessing.claims.application.port.in.InitiatePaymentUseCase;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

class InitiatePaymentServiceTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-03-01T10:15:30Z");
    private static final Instant APPROVED_AT = Instant.parse("2026-03-01T11:00:00Z");
    private static final Instant INITIATED_AT = Instant.parse("2026-03-01T12:00:00Z");
    private static final BigDecimal AMOUNT = new BigDecimal("150.00");

    private final Clock clock = Clock.fixed(INITIATED_AT, ZoneOffset.UTC);
    private final RecordingClaimRepository claims = new RecordingClaimRepository();
    private final FakePayment payments = new FakePayment();
    private final InitiatePaymentUseCase initiatePayment =
            new InitiatePaymentService(claims, payments, new PaymentRequestMapper(), clock);

    @Test
    void should_move_an_approved_claim_to_payment_pending() {
        // given
        Claim approved = approvedClaim();
        claims.store(approved);

        // when
        initiatePayment.initiate(new InitiatePaymentCommand(approved.id()));

        // then
        assertThat(payments.requests()).containsExactly(expectedRequest(approved));
        assertThat(claims.saveCount()).isEqualTo(1);
        Claim stored = claims.findById(approved.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ClaimStatus.PAYMENT_PENDING);
        assertThat(stored.rejectionReason()).isEmpty();
        assertThat(stored.events()).containsExactly(
                new ClaimAccepted(ACCEPTED_AT),
                new ClaimStatusChanged(
                        ClaimStatus.ACCEPTED,
                        ClaimStatus.AWAITING_ANALYST,
                        Optional.empty(),
                        APPROVED_AT),
                new ClaimStatusChanged(
                        ClaimStatus.AWAITING_ANALYST,
                        ClaimStatus.APPROVED,
                        Optional.empty(),
                        APPROVED_AT),
                new ClaimStatusChanged(
                        ClaimStatus.APPROVED,
                        ClaimStatus.PAYMENT_PENDING,
                        Optional.empty(),
                        INITIATED_AT));
    }

    @Test
    void should_leave_the_claim_approved_when_payment_is_unavailable() {
        // given
        Claim approved = approvedClaim();
        claims.store(approved);
        payments.willFail(new PaymentUnavailableException("Payment System is unavailable"));

        // when / then
        assertThatThrownBy(() -> initiatePayment.initiate(new InitiatePaymentCommand(approved.id())))
                .isInstanceOf(PaymentUnavailableException.class);
        assertThat(payments.requests()).containsExactly(expectedRequest(approved));
        assertThat(claims.saveCount()).isZero();
        Claim stored = claims.findById(approved.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(stored.events()).containsExactly(
                new ClaimAccepted(ACCEPTED_AT),
                new ClaimStatusChanged(
                        ClaimStatus.ACCEPTED,
                        ClaimStatus.AWAITING_ANALYST,
                        Optional.empty(),
                        APPROVED_AT),
                new ClaimStatusChanged(
                        ClaimStatus.AWAITING_ANALYST,
                        ClaimStatus.APPROVED,
                        Optional.empty(),
                        APPROVED_AT));
    }

    @Test
    void should_send_the_same_idempotency_key_when_payment_is_retried() {
        // given
        Claim approved = approvedClaim();
        claims.store(approved);
        payments.failOnce(new PaymentUnavailableException("Payment System is unavailable"));

        // when
        assertThatThrownBy(() -> initiatePayment.initiate(new InitiatePaymentCommand(approved.id())))
                .isInstanceOf(PaymentUnavailableException.class);
        initiatePayment.initiate(new InitiatePaymentCommand(approved.id()));

        // then
        PaymentRequest first = expectedRequest(approved);
        assertThat(payments.requests()).containsExactly(first, first);
        assertThat(claims.saveCount()).isEqualTo(1);
        assertThat(claims.findById(approved.id()).orElseThrow().status()).isEqualTo(ClaimStatus.PAYMENT_PENDING);
    }

    @Test
    void should_fail_when_the_claim_is_missing() {
        // given
        ClaimId id = new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        // when / then
        assertThatThrownBy(() -> initiatePayment.initiate(new InitiatePaymentCommand(id)))
                .isInstanceOf(ClaimNotFoundException.class)
                .hasMessageContaining(id.value().toString());
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, mode = Mode.EXCLUDE, names = "APPROVED")
    void should_refuse_before_calling_payment_when_the_claim_is_not_approved(ClaimStatus status) {
        // given
        Claim claim = claimIn(status);
        claims.store(claim);

        // when / then
        assertThatThrownBy(() -> initiatePayment.initiate(new InitiatePaymentCommand(claim.id())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(claim.id().value().toString())
                .hasMessageContaining(status.name());
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
        assertThat(claims.findById(claim.id()).orElseThrow().status()).isEqualTo(status);
    }

    @Test
    void should_reject_a_null_command() {
        // given / when / then
        assertThatThrownBy(() -> initiatePayment.initiate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_reject_a_null_claim_id() {
        // given / when / then
        assertThatThrownBy(() -> initiatePayment.initiate(new InitiatePaymentCommand(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimId");
        assertThat(payments.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    private static PaymentRequest expectedRequest(Claim claim) {
        return new PaymentRequest(
                claim.id().value().toString(), claim.id(), "C-1", "P-1", AMOUNT);
    }

    private static Claim approvedClaim() {
        Clock approvedAt = Clock.fixed(APPROVED_AT, ZoneOffset.UTC);
        return acceptedClaim()
                .transitionTo(ClaimStatus.AWAITING_ANALYST, approvedAt)
                .transitionTo(ClaimStatus.APPROVED, approvedAt);
    }

    private Claim claimIn(ClaimStatus status) {
        Claim accepted = acceptedClaim();
        return switch (status) {
            case ACCEPTED -> accepted;
            case AWAITING_ANALYST -> accepted.transitionTo(ClaimStatus.AWAITING_ANALYST, clock);
            case APPROVED -> approvedClaim();
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
            return claims.values().stream()
                    .filter(claim -> claim.claimType() == ClaimType.DEATH)
                    .filter(claim -> claim.status() == ClaimStatus.ACCEPTED
                            || claim.status() == ClaimStatus.AWAITING_ANALYST)
                    .toList();
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
