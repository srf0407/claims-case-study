package com.claimsprocessing.claims.adapter.in.schedule;

import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.CheckDeathClaimSlaUseCase;
import org.springframework.scheduling.annotation.Scheduled;

public final class DeathClaimSlaCheckScheduler {

    private final CheckDeathClaimSlaUseCase useCase;

    public DeathClaimSlaCheckScheduler(CheckDeathClaimSlaUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @Scheduled(
            fixedDelayString = "${claims.death-sla.check-interval}",
            initialDelayString = "${claims.death-sla.check-interval}")
    public void check() {
        useCase.check();
    }
}
