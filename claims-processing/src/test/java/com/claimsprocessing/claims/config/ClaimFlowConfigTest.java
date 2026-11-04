package com.claimsprocessing.claims.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;

import com.claimsprocessing.claims.application.AssessPolicyService;
import com.claimsprocessing.claims.application.ClaimWorkerService;
import com.claimsprocessing.claims.application.DecideClaimService;
import com.claimsprocessing.claims.application.InitiatePaymentService;
import com.claimsprocessing.claims.application.StraightThroughPaymentService;
import com.claimsprocessing.claims.application.ValidateClientService;
import com.claimsprocessing.claims.application.port.in.AssessPolicyUseCase;
import com.claimsprocessing.claims.application.port.in.ClaimWorkerUseCase;
import com.claimsprocessing.claims.application.port.in.DecideClaimUseCase;
import com.claimsprocessing.claims.application.port.in.InitiatePaymentUseCase;
import com.claimsprocessing.claims.application.port.in.StraightThroughPaymentUseCase;
import com.claimsprocessing.claims.application.port.in.ValidateClientUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = "claims.worker.poll-interval=PT24H")
class ClaimFlowConfigTest {

    private final ApplicationContext context;
    private final StraightThroughProperties straightThroughProperties;
    private final WorkerProperties workerProperties;

    @Autowired
    ClaimFlowConfigTest(
            ApplicationContext context,
            StraightThroughProperties straightThroughProperties,
            WorkerProperties workerProperties) {
        this.context = context;
        this.straightThroughProperties = straightThroughProperties;
        this.workerProperties = workerProperties;
    }

    @Test
    void should_load_the_default_straight_through_limit() {
        // given / when: @SpringBootTest binds claims.straight-through.limit

        // then
        assertThat(straightThroughProperties.limit()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    void should_bind_the_worker_poll_interval() {
        // given / when: this context sets claims.worker.poll-interval to PT24H

        // then
        assertThat(workerProperties.pollInterval()).isEqualTo(Duration.parse("PT24H"));
    }

    @Test
    void should_create_the_claim_flow_beans() {
        // given / when: @SpringBootTest starts the application context

        // then
        assertThat(context.getBean(ClaimWorkerUseCase.class)).isInstanceOf(ClaimWorkerService.class);
        assertThat(context.getBean(ValidateClientUseCase.class)).isInstanceOf(ValidateClientService.class);
        assertThat(context.getBean(AssessPolicyUseCase.class)).isInstanceOf(AssessPolicyService.class);
        assertThat(context.getBean(StraightThroughPaymentUseCase.class))
                .isInstanceOf(StraightThroughPaymentService.class);
        assertThat(context.getBean(InitiatePaymentUseCase.class)).isInstanceOf(InitiatePaymentService.class);
        assertThat(context.getBean(DecideClaimUseCase.class)).isInstanceOf(DecideClaimService.class);
    }
}
