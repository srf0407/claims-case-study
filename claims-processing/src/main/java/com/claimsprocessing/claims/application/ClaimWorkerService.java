package com.claimsprocessing.claims.application;

import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.AssessPolicyCommand;
import com.claimsprocessing.claims.application.port.in.AssessPolicyUseCase;
import com.claimsprocessing.claims.application.port.in.ClaimWorkerUseCase;
import com.claimsprocessing.claims.application.port.in.StraightThroughPaymentCommand;
import com.claimsprocessing.claims.application.port.in.StraightThroughPaymentUseCase;
import com.claimsprocessing.claims.application.port.in.ValidateClientCommand;
import com.claimsprocessing.claims.application.port.in.ValidateClientUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimQueuePort;
import com.claimsprocessing.claims.application.port.out.ClaimQueuedMessage;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryUnavailableException;
import com.claimsprocessing.claims.application.port.out.ClientRegistryUnavailableException;
import com.claimsprocessing.claims.application.port.out.PaymentUnavailableException;
import com.claimsprocessing.claims.application.port.out.PolicyManagerUnavailableException;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.IllegalClaimTransitionException;

public final class ClaimWorkerService implements ClaimWorkerUseCase {

    /** Placeholder. Who sets the flag stays on the existing Open table row. */
    private static final boolean FLAGGED = false;

    private final ClaimQueuePort claimQueue;
    private final ValidateClientUseCase validateClient;
    private final AssessPolicyUseCase assessPolicy;
    private final StraightThroughPaymentUseCase straightThroughPayment;
    private final ClaimWorkerResultMapper claimWorkerResultMapper;

    public ClaimWorkerService(
            ClaimQueuePort claimQueue,
            ValidateClientUseCase validateClient,
            AssessPolicyUseCase assessPolicy,
            StraightThroughPaymentUseCase straightThroughPayment,
            ClaimWorkerResultMapper claimWorkerResultMapper) {
        this.claimQueue = Objects.requireNonNull(claimQueue, "claimQueue");
        this.validateClient = Objects.requireNonNull(validateClient, "validateClient");
        this.assessPolicy = Objects.requireNonNull(assessPolicy, "assessPolicy");
        this.straightThroughPayment = Objects.requireNonNull(straightThroughPayment, "straightThroughPayment");
        this.claimWorkerResultMapper =
                Objects.requireNonNull(claimWorkerResultMapper, "claimWorkerResultMapper");
    }

    @Override
    public ClaimWorkerResult process() {
        var received = claimQueue.receive();
        if (received.isEmpty()) {
            return ClaimWorkerResult.QUEUE_EMPTY;
        }
        ClaimQueuedMessage message = received.orElseThrow();
        try {
            ClaimWorkerResult result = handle(message);
            claimQueue.acknowledge(message);
            return result;
        } catch (IllegalStateException | IllegalClaimTransitionException | ClaimNotFoundException exception) {
            claimQueue.acknowledge(message);
            throw exception;
        } catch (ClientRegistryUnavailableException
                | PolicyManagerUnavailableException
                | PaymentUnavailableException
                | ClaimRepositoryUnavailableException exception) {
            if (message.claimType() != ClaimType.DEATH) {
                claimQueue.release(message);
            }
            throw exception;
        }
    }

    private ClaimWorkerResult handle(ClaimQueuedMessage message) {
        ClientCheckResult clientCheck = validateClient.validate(new ValidateClientCommand(message.claimId()));
        if (clientCheck != ClientCheckResult.KNOWN) {
            return ClaimWorkerResult.REJECTED;
        }
        PolicyAssessmentResult policyAssessment =
                assessPolicy.assess(new AssessPolicyCommand(message.claimId()));
        if (policyAssessment != PolicyAssessmentResult.COVERED) {
            return ClaimWorkerResult.REJECTED;
        }
        StraightThroughResult straightThroughResult = straightThroughPayment.execute(
                new StraightThroughPaymentCommand(
                        message.claimId(), clientCheck, policyAssessment, FLAGGED));
        return claimWorkerResultMapper.map(straightThroughResult);
    }
}
