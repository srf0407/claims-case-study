package com.claimsprocessing.claims.config;

import java.time.Clock;

import com.claimsprocessing.claims.adapter.in.web.GetClaimMapper;
import com.claimsprocessing.claims.adapter.in.web.PaymentCallbackMapper;
import com.claimsprocessing.claims.adapter.in.web.PaymentSignatureVerifier;
import com.claimsprocessing.claims.adapter.in.web.SubmitClaimMapper;
import com.claimsprocessing.claims.adapter.out.queue.InMemoryPriorityClaimQueue;
import com.claimsprocessing.claims.application.ApplyPaymentStatusService;
import com.claimsprocessing.claims.application.ClaimQueuedMessageMapper;
import com.claimsprocessing.claims.application.GetClaimService;
import com.claimsprocessing.claims.application.PaymentOutcomeMapper;
import com.claimsprocessing.claims.application.SubmitClaimService;
import com.claimsprocessing.claims.application.port.in.ApplyPaymentStatusUseCase;
import com.claimsprocessing.claims.application.port.in.GetClaimUseCase;
import com.claimsprocessing.claims.application.port.in.SubmitClaimUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimQueuePort;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class UseCaseConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Profile("!localstack")
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

    @Bean
    GetClaimMapper getClaimMapper() {
        return new GetClaimMapper();
    }

    @Bean
    GetClaimUseCase getClaimUseCase(ClaimRepositoryPort claimRepository) {
        return new GetClaimService(claimRepository);
    }

    @Bean
    ApplyPaymentStatusUseCase applyPaymentStatusUseCase(ClaimRepositoryPort claimRepository, Clock clock) {
        return new ApplyPaymentStatusService(claimRepository, new PaymentOutcomeMapper(), clock);
    }

    @Bean
    PaymentSignatureVerifier paymentSignatureVerifier(PaymentProperties paymentProperties) {
        return new PaymentSignatureVerifier(paymentProperties.callbackSecret());
    }

    @Bean
    PaymentCallbackMapper paymentCallbackMapper(ObjectMapper objectMapper) {
        return new PaymentCallbackMapper(objectMapper);
    }
}
