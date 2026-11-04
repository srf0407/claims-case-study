package com.claimsprocessing.claims.application;

import java.time.Clock;
import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.ApplyPaymentStatusCommand;
import com.claimsprocessing.claims.application.port.in.ApplyPaymentStatusUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;

public final class ApplyPaymentStatusService implements ApplyPaymentStatusUseCase {

    private final ClaimRepositoryPort claimRepository;
    private final PaymentOutcomeMapper paymentOutcomeMapper;
    private final Clock clock;

    public ApplyPaymentStatusService(
            ClaimRepositoryPort claimRepository, PaymentOutcomeMapper paymentOutcomeMapper, Clock clock) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.paymentOutcomeMapper = Objects.requireNonNull(paymentOutcomeMapper, "paymentOutcomeMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PaymentStatusResult apply(ApplyPaymentStatusCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.claimId(), "claimId");
        Objects.requireNonNull(command.outcome(), "outcome");
        Objects.requireNonNull(command.idempotencyKey(), "idempotencyKey");
        if (!command.idempotencyKey().equals(command.claimId().value().toString())) {
            throw new IllegalArgumentException("idempotencyKey mismatch");
        }
        Claim claim = claimRepository.findById(command.claimId())
                .orElseThrow(() -> new ClaimNotFoundException(command.claimId()));
        ClaimStatus target = paymentOutcomeMapper.map(command.outcome());
        if (claim.status() == target) {
            return PaymentStatusResult.DUPLICATE;
        }
        if (claim.status() != ClaimStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Claim " + claim.id().value() + " is " + claim.status().name());
        }
        Claim updated = claim.transitionTo(target, clock);
        if (!claimRepository.replaceIfStatus(updated, ClaimStatus.PAYMENT_PENDING)) {
            return lostRace(command.claimId(), target);
        }
        return PaymentStatusResult.APPLIED;
    }

    private PaymentStatusResult lostRace(ClaimId claimId, ClaimStatus target) {
        Claim current = claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));
        if (current.status() == target) {
            return PaymentStatusResult.DUPLICATE;
        }
        throw new IllegalStateException("Claim " + current.id().value() + " is " + current.status().name());
    }
}
