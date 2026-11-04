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

import com.claimsprocessing.claims.application.port.in.ValidateClientCommand;
import com.claimsprocessing.claims.application.port.in.ValidateClientUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.ClientRegistryPort;
import com.claimsprocessing.claims.application.port.out.ClientRegistryUnavailableException;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimAccepted;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimStatusChanged;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.RejectionReason;
import org.junit.jupiter.api.Test;

class ValidateClientServiceTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-03-01T10:15:30Z");
    private static final Instant CHECKED_AT = Instant.parse("2026-03-01T12:00:00Z");

    private final Clock clock = Clock.fixed(CHECKED_AT, ZoneOffset.UTC);
    private final RecordingClaimRepository claims = new RecordingClaimRepository();
    private final FakeClientRegistry registry = new FakeClientRegistry();
    private final ValidateClientUseCase validateClient = new ValidateClientService(claims, registry, clock);

    @Test
    void should_reject_the_claim_when_the_client_is_unknown() {
        // given
        Claim accepted = acceptedClaim();
        claims.store(accepted);
        registry.willAnswer(ClientCheckResult.UNKNOWN);

        // when
        ClientCheckResult result = validateClient.validate(new ValidateClientCommand(accepted.id()));

        // then
        assertThat(result).isEqualTo(ClientCheckResult.UNKNOWN);
        assertThat(registry.checkedClientId()).isEqualTo("C-1");
        assertThat(claims.saveCount()).isEqualTo(1);
        Claim stored = claims.findById(accepted.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(stored.rejectionReason()).contains(RejectionReason.UNKNOWN_CLIENT);
        assertThat(stored.events()).containsExactly(
                new ClaimAccepted(ACCEPTED_AT),
                new ClaimStatusChanged(
                        ClaimStatus.ACCEPTED,
                        ClaimStatus.REJECTED,
                        Optional.of(RejectionReason.UNKNOWN_CLIENT),
                        CHECKED_AT));
    }

    @Test
    void should_leave_the_claim_accepted_when_the_client_is_known() {
        // given
        Claim accepted = acceptedClaim();
        claims.store(accepted);
        registry.willAnswer(ClientCheckResult.KNOWN);

        // when
        ClientCheckResult result = validateClient.validate(new ValidateClientCommand(accepted.id()));

        // then
        assertThat(result).isEqualTo(ClientCheckResult.KNOWN);
        assertThat(registry.checkedClientId()).isEqualTo("C-1");
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
        assertThatThrownBy(() -> validateClient.validate(new ValidateClientCommand(id)))
                .isInstanceOf(ClaimNotFoundException.class)
                .hasMessageContaining(id.value().toString());
        assertThat(registry.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_fail_when_the_claim_is_not_accepted() {
        // given
        Claim awaitingAnalyst = acceptedClaim().transitionTo(ClaimStatus.AWAITING_ANALYST, clock);
        claims.store(awaitingAnalyst);

        // when / then
        assertThatThrownBy(() -> validateClient.validate(new ValidateClientCommand(awaitingAnalyst.id())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(awaitingAnalyst.id().value().toString())
                .hasMessageContaining(ClaimStatus.AWAITING_ANALYST.name());
        assertThat(registry.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
        assertThat(claims.findById(awaitingAnalyst.id()).orElseThrow().status())
                .isEqualTo(ClaimStatus.AWAITING_ANALYST);
    }

    @Test
    void should_leave_the_claim_unchanged_when_the_registry_is_unavailable() {
        // given
        Claim accepted = acceptedClaim();
        claims.store(accepted);
        registry.willFail(new ClientRegistryUnavailableException("Client Registry is unavailable"));

        // when / then
        assertThatThrownBy(() -> validateClient.validate(new ValidateClientCommand(accepted.id())))
                .isInstanceOf(ClientRegistryUnavailableException.class);
        assertThat(claims.saveCount()).isZero();
        Claim stored = claims.findById(accepted.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(stored.events()).containsExactly(new ClaimAccepted(ACCEPTED_AT));
    }

    @Test
    void should_reject_a_null_command() {
        // given / when / then
        assertThatThrownBy(() -> validateClient.validate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
        assertThat(registry.callCount()).isZero();
        assertThat(claims.saveCount()).isZero();
    }

    @Test
    void should_reject_a_null_claim_id() {
        // given / when / then
        assertThatThrownBy(() -> validateClient.validate(new ValidateClientCommand(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimId");
        assertThat(registry.callCount()).isZero();
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

    private static final class FakeClientRegistry implements ClientRegistryPort {

        private ClientCheckResult result = ClientCheckResult.KNOWN;
        private RuntimeException failure;
        private int callCount;
        private String checkedClientId;

        void willAnswer(ClientCheckResult result) {
            this.result = result;
            this.failure = null;
        }

        void willFail(RuntimeException failure) {
            this.failure = failure;
        }

        int callCount() {
            return callCount;
        }

        String checkedClientId() {
            return checkedClientId;
        }

        @Override
        public ClientCheckResult check(String clientId, ClaimType claimType) {
            callCount++;
            checkedClientId = clientId;
            if (claimType == null) {
                throw new NullPointerException("claimType");
            }
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
