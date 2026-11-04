package com.claimsprocessing.claims.config;

import java.time.Clock;

import com.claimsprocessing.claims.adapter.in.web.SubmitClaimMapper;
import com.claimsprocessing.claims.adapter.out.queue.InMemoryPriorityClaimQueue;
import com.claimsprocessing.claims.application.ClaimQueuedMessageMapper;
import com.claimsprocessing.claims.application.SubmitClaimService;
import com.claimsprocessing.claims.application.port.in.SubmitClaimUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimQueuePort;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ClaimQueuePort claimQueuePort() {
        return new InMemoryPriorityClaimQueue();
    }

    @Bean
    SubmitClaimMapper submitClaimMapper() {
        return new SubmitClaimMapper();
    }

    @Bean
    SubmitClaimUseCase submitClaimUseCase(
            ClaimRepositoryPort claimRepository, ClaimQueuePort claimQueue, Clock clock) {
        return new SubmitClaimService(claimRepository, claimQueue, new ClaimQueuedMessageMapper(), clock);
    }
}
