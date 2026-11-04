package com.claimsprocessing.claims.adapter.in.schedule;

import java.util.Objects;

import com.claimsprocessing.claims.application.ClaimWorkerResult;
import com.claimsprocessing.claims.application.port.in.ClaimWorkerUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class ClaimWorkerScheduler {

    private static final int MAX_PER_TICK = 10;

    private static final Logger log = LoggerFactory.getLogger(ClaimWorkerScheduler.class);

    private final ClaimWorkerUseCase useCase;

    public ClaimWorkerScheduler(ClaimWorkerUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @Scheduled(
            fixedDelayString = "${claims.worker.poll-interval}",
            initialDelayString = "${claims.worker.poll-interval}")
    public void poll() {
        for (int processed = 0; processed < MAX_PER_TICK; processed++) {
            try {
                ClaimWorkerResult result = useCase.process();
                if (result == ClaimWorkerResult.QUEUE_EMPTY) {
                    return;
                }
            } catch (Exception exception) {
                log.error("Claim worker failed", exception);
                return;
            }
        }
    }
}
