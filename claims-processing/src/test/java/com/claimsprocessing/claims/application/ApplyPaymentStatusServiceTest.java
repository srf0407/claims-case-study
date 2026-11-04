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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.in.ApplyPaymentStatusCommand;
import com.claimsprocessing.claims.application.port.in.ApplyPaymentStatusUseCase;
import com.claimsprocessing.claims.application.port.in.PaymentOutcome;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
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

class ApplyPaymentStatusServiceTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-03-01T10:15:30Z");
    private static final Instant PENDING_AT = Instant.parse("2026-03-01T11:00:00Z");
    private static final Instant APPLIED_AT = Instant.parse("2026-03-01T12:00:00Z");
    private static final BigDecimal AMOUNT = new BigDecimal("150.00");

    private final Clock clock = Clock.fixed(APPLIED_AT, ZoneOffset.UTC);
    private final RecordingClaimRepository claims = new RecordingClaimRepository();
    private final ApplyPaymentStatusUseCase applyPaymentStatus =
            new ApplyPaymentStatusService(claims, new PaymentOutcomeMapper(), clock);

    @Test
    void should_mark_a_pending_claim_paid() {
        // given
        Claim pending = pendingClaim();
        claims.store(pending);

        // when
        PaymentStatusResult result = applyPaymentStatus.apply(command(pending, PaymentOutcome.PAID));

        // then
        assertThat(result).isEqualTo(PaymentStatusResult.APPLIED);
        assertThat(claims.saveCount()).isEqualTo(1);
        Claim stored = claims.stored(pending.id());
        assertThat(stored.status()).isEqualTo(ClaimStatus.PAID);
        assertThat(stored.rejectionReason()).isEmpty();
        assertThat(stored.events()).containsExactly(
                new ClaimAccepted(ACCEPTED_AT),
                new ClaimStatusChanged(
                        ClaimStatus.ACCEPTED,
                        ClaimStatus.PAYMENT_PENDING,
                        Optional.empty(),
                        PENDING_AT),
                new ClaimStatusChanged(
                        ClaimStatus.PAYMENT_PENDING,
                        ClaimStatus.PAID,
                        Optional.empty(),
                        APPLIED_AT));
    }

    @Test
    void should_mark_a_pending_claim_payment_failed() {
        // given
        Claim pending = pendingClaim();
        claims.store(pending);

        // when
        PaymentStatusResult result = applyPaymentStatus.apply(command(pending, PaymentOutcome.FAILED));

        // then
        assertThat(result).isEqualTo(PaymentStatusResult.APPLIED);
        assertThat(claims.saveCount()).isEqualTo(1);
        Claim stored = claims.stored(pending.id());
        assertThat(stored.status()).isEqualTo(ClaimStatus.PAYMENT_FAILED);
        assertThat(stored.rejectionReason()).isEmpty();
        assertThat(stored.events()).containsExactly(
                new ClaimAccepted(ACCEPTED_AT),
                new ClaimStatusChanged(
                        ClaimStatus.ACCEPTED,
                        ClaimStatus.PAYMENT_PENDING,
                        Optional.empty(),
                        PENDING_AT),
                new ClaimStatusChanged(
                        ClaimStatus.PAYMENT_PENDING,
                        ClaimStatus.PAYMENT_FAILED,
                        Optional.empty(),
                        APPLIED_AT));
    }

    @Test
    void should_report_a_duplicate_when_the_claim_is_already_paid() {
        // given
        Claim paid = claimIn(ClaimStatus.PAID);
        claims.store(paid);

        // when
        PaymentStatusResult result = applyPaymentStatus.apply(command(paid, PaymentOutcome.PAID));

        // then
        assertThat(result).isEqualTo(PaymentStatusResult.DUPLICATE);
        assertThat(claims.saveCount()).isZero();
        Claim stored = claims.stored(paid.id());
        assertThat(stored.status()).isEqualTo(ClaimStatus.PAID);
        assertThat(stored.events()).isEqualTo(paid.events());
    }

    @Test
    void should_report_a_duplicate_when_payment_already_failed() {
        // given
        Claim failed = claimIn(ClaimStatus.PAYMENT_FAILED);
        claims.store(failed);

        // when
        PaymentStatusResult result = applyPaymentStatus.apply(command(failed, PaymentOutcome.FAILED));

        // then
        assertThat(result).isEqualTo(PaymentStatusResult.DUPLICATE);
        assertThat(claims.saveCount()).isZero();
        Claim stored = claims.stored(failed.id());
        assertThat(stored.status()).isEqualTo(ClaimStatus.PAYMENT_FAILED);
        assertThat(stored.events()).isEqualTo(failed.events());
    }

    @Test
    void should_refuse_a_failed_outcome_for_a_paid_claim() {
        // given
        Claim paid = claimIn(ClaimStatus.PAID);
        claims.store(paid);

        // when / then
        assertThatThrownBy(() -> applyPaymentStatus.apply(command(paid, PaymentOutcome.FAILED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Claim " + paid.id().value() + " is PAID");
        assertThat(claims.saveCount()).isZero();
        Claim stored = claims.stored(paid.id());
        assertThat(stored.status()).isEqualTo(ClaimStatus.PAID);
        assertThat(stored.events()).isEqualTo(paid.events());
    }

    @Test
    void should_refuse_a_paid_outcome_for_a_failed_claim() {
        // given
        Claim failed = claimIn(ClaimStatus.PAYMENT_FAILED);
        claims.store(failed);

        // when / then
        assertThatThrownBy(() -> applyPaymentStatus.apply(command(failed, PaymentOutcome.PAID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Claim " + failed.id().value() + " is PAYMENT_FAILED");
        assertThat(claims.saveCount()).isZero();
        Claim stored = claims.stored(failed.id());
        assertThat(stored.status()).isEqualTo(ClaimStatus.PAYMENT_FAILED);
        assertThat(stored.events()).isEqualTo(failed.events());
    }

    @ParameterizedTest
    @EnumSource(
            value = ClaimStatus.class,
            mode = Mode.EXCLUDE,
            names = {"PAYMENT_PENDING", "PAID", "PAYMENT_FAILED"})
    void should_refuse_when_the_claim_is_not_pending_paid_or_payment_failed(ClaimStatus status) {
        // given
        Claim claim = claimIn(status);
        claims.store(claim);

        // when / then
        for (PaymentOutcome outcome : PaymentOutcome.values()) {
            assertThatThrownBy(() -> applyPaymentStatus.apply(command(claim, outcome)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Claim " + claim.id().value() + " is " + status.name());
        }
        assertThat(claims.saveCount()).isZero();
        Claim stored = claims.stored(claim.id());
        assertThat(stored.status()).isEqualTo(status);
        assertThat(stored.events()).isEqualTo(claim.events());
    }

    @Test
    void should_fail_when_the_claim_is_missing() {
        // given
        ClaimId id = new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        // when / then
        assertThatThrownBy(() -> applyPaymentStatus.apply(
                        new ApplyPaymentStatusCommand(id, PaymentOutcome.PAID, id.value().toString())))
                .isInstanceOf(ClaimNotFoundException.class)
                .hasMessageContaining(id.value().toString());
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_reject_an_idempotency_key_that_is_not_the_claim_id() {
        // given
        Claim pending = pendingClaim();
        claims.store(pending);
        String claimKey = pending.id().value().toString();
        List<String> keys = List.of(
                "22222222-2222-2222-2222-222222222222",
                "",
                claimKey.toUpperCase(Locale.ROOT));

        // when / then
        for (String key : keys) {
            assertThatThrownBy(() -> applyPaymentStatus.apply(
                            new ApplyPaymentStatusCommand(pending.id(), PaymentOutcome.PAID, key)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("idempotencyKey mismatch");
        }
        assertThat(claims.findCount()).isZero();
        assertThat(claims.saveCount()).isZero();
        assertThat(claims.stored(pending.id()).status()).isEqualTo(ClaimStatus.PAYMENT_PENDING);
        assertThat(claims.stored(pending.id()).events()).isEqualTo(pending.events());
    }

    @Test
    void should_reject_a_null_command() {
        // given / when / then
        assertThatThrownBy(() -> applyPaymentStatus.apply(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
        assertThat(claims.findCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_reject_a_null_claim_id() {
        // given / when / then
        assertThatThrownBy(() -> applyPaymentStatus.apply(
                        new ApplyPaymentStatusCommand(null, PaymentOutcome.PAID, "not-read")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimId");
        assertThat(claims.findCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_reject_a_null_outcome() {
        // given
        ClaimId id = new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        // when / then
        assertThatThrownBy(() -> applyPaymentStatus.apply(
                        new ApplyPaymentStatusCommand(id, null, id.value().toString())))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("outcome");
        assertThat(claims.findCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_reject_a_null_idempotency_key() {
        // given
        ClaimId id = new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        // when / then
        assertThatThrownBy(() -> applyPaymentStatus.apply(
                        new ApplyPaymentStatusCommand(id, PaymentOutcome.PAID, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("idempotencyKey");
        assertThat(claims.findCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    private static ApplyPaymentStatusCommand command(Claim claim, PaymentOutcome outcome) {
        return new ApplyPaymentStatusCommand(claim.id(), outcome, claim.id().value().toString());
    }

    private static Claim pendingClaim() {
        return acceptedClaim().transitionTo(ClaimStatus.PAYMENT_PENDING, Clock.fixed(PENDING_AT, ZoneOffset.UTC));
    }

    private Claim claimIn(ClaimStatus status) {
        Claim accepted = acceptedClaim();
        Clock pendingAt = Clock.fixed(PENDING_AT, ZoneOffset.UTC);
        return switch (status) {
            case ACCEPTED -> accepted;
            case AWAITING_ANALYST -> accepted.transitionTo(ClaimStatus.AWAITING_ANALYST, pendingAt);
            case APPROVED -> accepted.transitionTo(ClaimStatus.AWAITING_ANALYST, pendingAt)
                    .transitionTo(ClaimStatus.APPROVED, pendingAt);
            case PAYMENT_PENDING -> accepted.transitionTo(ClaimStatus.PAYMENT_PENDING, pendingAt);
            case PAID -> accepted.transitionTo(ClaimStatus.PAYMENT_PENDING, pendingAt)
                    .transitionTo(ClaimStatus.PAID, pendingAt);
            case PAYMENT_FAILED -> accepted.transitionTo(ClaimStatus.PAYMENT_PENDING, pendingAt)
                    .transitionTo(ClaimStatus.PAYMENT_FAILED, pendingAt);
            case REJECTED -> accepted.reject(RejectionReason.UNKNOWN_CLIENT, pendingAt);
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
        private int findCount;

        void store(Claim claim) {
            claims.put(claim.id(), claim);
        }

        Claim stored(ClaimId id) {
            return claims.get(id);
        }

        int saveCount() {
            return saveCount;
        }

        int findCount() {
            return findCount;
        }

        @Override
        public void save(Claim claim) {
            saveCount++;
            claims.put(claim.id(), claim);
        }

        @Override
        public Optional<Claim> findById(ClaimId id) {
            findCount++;
            return Optional.ofNullable(claims.get(id));
        }
    }
}
