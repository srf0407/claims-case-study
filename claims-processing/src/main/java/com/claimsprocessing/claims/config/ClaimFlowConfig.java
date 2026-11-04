package com.claimsprocessing.claims.config;

import java.time.Clock;

import com.claimsprocessing.claims.adapter.in.schedule.ClaimWorkerScheduler;
import com.claimsprocessing.claims.adapter.in.web.DecideClaimMapper;
import com.claimsprocessing.claims.application.AssessPolicyService;
import com.claimsprocessing.claims.application.ClaimWorkerResultMapper;
import com.claimsprocessing.claims.application.ClaimWorkerService;
import com.claimsprocessing.claims.application.DecideClaimService;
import com.claimsprocessing.claims.application.InitiatePaymentService;
import com.claimsprocessing.claims.application.PaymentRequestMapper;
import com.claimsprocessing.claims.application.StraightThroughPaymentService;
import com.claimsprocessing.claims.application.ValidateClientService;
import com.claimsprocessing.claims.application.port.in.AssessPolicyUseCase;
import com.claimsprocessing.claims.application.port.in.ClaimWorkerUseCase;
import com.claimsprocessing.claims.application.port.in.DecideClaimUseCase;
import com.claimsprocessing.claims.application.port.in.InitiatePaymentUseCase;
import com.claimsprocessing.claims.application.port.in.StraightThroughPaymentUseCase;
import com.claimsprocessing.claims.application.port.in.ValidateClientUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimQueuePort;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.ClientRegistryPort;
import com.claimsprocessing.claims.application.port.out.PaymentPort;
import com.claimsprocessing.claims.application.port.out.PolicyManagerPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StraightThroughProperties.class, WorkerProperties.class})
public class ClaimFlowConfig {

    @Bean
    ValidateClientUseCase validateClientUseCase(
            ClaimRepositoryPort claimRepository, ClientRegistryPort clientRegistry, Clock clock) {
        return new ValidateClientService(claimRepository, clientRegistry, clock);
    }

    @Bean
    AssessPolicyUseCase assessPolicyUseCase(
            ClaimRepositoryPort claimRepository, PolicyManagerPort policyManager, Clock clock) {
        return new AssessPolicyService(claimRepository, policyManager, clock);
    }

    @Bean
    StraightThroughPaymentUseCase straightThroughPaymentUseCase(
            ClaimRepositoryPort claimRepository,
            PaymentPort payment,
            Clock clock,
            StraightThroughProperties straightThroughProperties) {
        return new StraightThroughPaymentService(
                claimRepository, payment, new PaymentRequestMapper(), clock, straightThroughProperties.limit());
    }

    @Bean
    InitiatePaymentUseCase initiatePaymentUseCase(
            ClaimRepositoryPort claimRepository, PaymentPort payment, Clock clock) {
        return new InitiatePaymentService(claimRepository, payment, new PaymentRequestMapper(), clock);
    }

    @Bean
    ClaimWorkerUseCase claimWorkerUseCase(
            ClaimQueuePort claimQueue,
            ValidateClientUseCase validateClient,
            AssessPolicyUseCase assessPolicy,
            StraightThroughPaymentUseCase straightThroughPayment) {
        return new ClaimWorkerService(
                claimQueue, validateClient, assessPolicy, straightThroughPayment, new ClaimWorkerResultMapper());
    }

    @Bean
    DecideClaimMapper decideClaimMapper() {
        return new DecideClaimMapper();
    }

    @Bean
    DecideClaimUseCase decideClaimUseCase(
            ClaimRepositoryPort claimRepository, InitiatePaymentUseCase initiatePayment, Clock clock) {
        return new DecideClaimService(claimRepository, initiatePayment, clock);
    }

    @Bean
    ClaimWorkerScheduler claimWorkerScheduler(ClaimWorkerUseCase claimWorker) {
        return new ClaimWorkerScheduler(claimWorker);
    }
}
