package com.claimsprocessing.claims.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.in.AssessPolicyCommand;
import com.claimsprocessing.claims.application.port.in.AssessPolicyUseCase;
import com.claimsprocessing.claims.application.port.in.ClaimWorkerUseCase;
import com.claimsprocessing.claims.application.port.in.StraightThroughPaymentCommand;
import com.claimsprocessing.claims.application.port.in.StraightThroughPaymentUseCase;
import com.claimsprocessing.claims.application.port.in.ValidateClientCommand;
import com.claimsprocessing.claims.application.port.in.ValidateClientUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimQueuePort;
import com.claimsprocessing.claims.application.port.out.ClaimQueueUnavailableException;
import com.claimsprocessing.claims.application.port.out.ClaimQueuedMessage;
import com.claimsprocessing.claims.application.port.out.ClientRegistryUnavailableException;
import com.claimsprocessing.claims.application.port.out.InMemoryClaimQueue;
import com.claimsprocessing.claims.application.port.out.PaymentUnavailableException;
import com.claimsprocessing.claims.application.port.out.PolicyManagerUnavailableException;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import org.junit.jupiter.api.Test;

class ClaimWorkerServiceTest {

    private final InMemoryClaimQueue claimQueue = new InMemoryClaimQueue();
    private final FakeValidateClient validateClient = new FakeValidateClient();
    private final FakeAssessPolicy assessPolicy = new FakeAssessPolicy();
    private final FakeStraightThroughPayment straightThroughPayment = new FakeStraightThroughPayment();
    private final ClaimWorkerResultMapper claimWorkerResultMapper = new ClaimWorkerResultMapper();
    private final ClaimWorkerUseCase worker = new ClaimWorkerService(
            claimQueue, validateClient, assessPolicy, straightThroughPayment, claimWorkerResultMapper);

    @Test
    void should_reject_a_null_claim_queue() {
        // given / when / then
        assertThatThrownBy(() -> new ClaimWorkerService(
                        null, validateClient, assessPolicy, straightThroughPayment, claimWorkerResultMapper))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimQueue");
    }

    @Test
    void should_reject_a_null_validate_client() {
        // given / when / then
        assertThatThrownBy(() -> new ClaimWorkerService(
                        claimQueue, null, assessPolicy, straightThroughPayment, claimWorkerResultMapper))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("validateClient");
    }

    @Test
    void should_reject_a_null_assess_policy() {
        // given / when / then
        assertThatThrownBy(() -> new ClaimWorkerService(
                        claimQueue, validateClient, null, straightThroughPayment, claimWorkerResultMapper))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("assessPolicy");
    }

    @Test
    void should_reject_a_null_straight_through_payment() {
        // given / when / then
        assertThatThrownBy(() -> new ClaimWorkerService(
                        claimQueue, validateClient, assessPolicy, null, claimWorkerResultMapper))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("straightThroughPayment");
    }

    @Test
    void should_reject_a_null_claim_worker_result_mapper() {
        // given / when / then
        assertThatThrownBy(() -> new ClaimWorkerService(
                        claimQueue, validateClient, assessPolicy, straightThroughPayment, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimWorkerResultMapper");
    }

    @Test
    void should_return_queue_empty_and_call_nothing_when_the_queue_is_empty() {
        // given / when
        ClaimWorkerResult result = worker.process();

        // then
        assertThat(result).isEqualTo(ClaimWorkerResult.QUEUE_EMPTY);
        assertThat(validateClient.commands()).isEmpty();
        assertThat(assessPolicy.commands()).isEmpty();
        assertThat(straightThroughPayment.commands()).isEmpty();
    }

    @Test
    void should_reject_and_stop_when_the_client_is_unknown() {
        // given
        ClaimId claimId = enqueue(ClaimType.FUNERAL);
        validateClient.willAnswer(ClientCheckResult.UNKNOWN);

        // when
        ClaimWorkerResult result = worker.process();

        // then
        assertThat(result).isEqualTo(ClaimWorkerResult.REJECTED);
        assertThat(validateClient.commands()).containsExactly(new ValidateClientCommand(claimId));
        assertThat(assessPolicy.commands()).isEmpty();
        assertThat(straightThroughPayment.commands()).isEmpty();
        assertThat(claimQueue.messages()).isEmpty();
    }

    @Test
    void should_reject_and_stop_when_the_policy_is_lapsed() {
        // given
        ClaimId claimId = enqueue(ClaimType.DISABILITY);
        validateClient.willAnswer(ClientCheckResult.KNOWN);
        assessPolicy.willAnswer(PolicyAssessmentResult.LAPSED);

        // when
        ClaimWorkerResult result = worker.process();

        // then
        assertThat(result).isEqualTo(ClaimWorkerResult.REJECTED);
        assertThat(validateClient.commands()).containsExactly(new ValidateClientCommand(claimId));
        assertThat(assessPolicy.commands()).containsExactly(new AssessPolicyCommand(claimId));
        assertThat(straightThroughPayment.commands()).isEmpty();
    }

    @Test
    void should_reject_and_stop_when_the_policy_has_no_cover() {
        // given
        ClaimId claimId = enqueue(ClaimType.FUNERAL);
        validateClient.willAnswer(ClientCheckResult.KNOWN);
        assessPolicy.willAnswer(PolicyAssessmentResult.NO_COVER);

        // when
        ClaimWorkerResult result = worker.process();

        // then
        assertThat(result).isEqualTo(ClaimWorkerResult.REJECTED);
        assertThat(validateClient.commands()).containsExactly(new ValidateClientCommand(claimId));
        assertThat(assessPolicy.commands()).containsExactly(new AssessPolicyCommand(claimId));
        assertThat(straightThroughPayment.commands()).isEmpty();
    }

    @Test
    void should_return_payment_initiated_for_a_covered_known_client() {
        // given
        ClaimId claimId = enqueue(ClaimType.FUNERAL);
        validateClient.willAnswer(ClientCheckResult.KNOWN);
        assessPolicy.willAnswer(PolicyAssessmentResult.COVERED);
        straightThroughPayment.willAnswer(StraightThroughResult.PAYMENT_INITIATED);

        // when
        ClaimWorkerResult result = worker.process();

        // then
        assertThat(result).isEqualTo(ClaimWorkerResult.PAYMENT_INITIATED);
        assertThat(validateClient.commands()).containsExactly(new ValidateClientCommand(claimId));
        assertThat(assessPolicy.commands()).containsExactly(new AssessPolicyCommand(claimId));
        assertThat(straightThroughPayment.commands()).containsExactly(new StraightThroughPaymentCommand(
                claimId, ClientCheckResult.KNOWN, PolicyAssessmentResult.COVERED, false));
    }

    @Test
    void should_return_sent_to_analyst_for_a_covered_known_client() {
        // given
        ClaimId claimId = enqueue(ClaimType.DISABILITY);
        validateClient.willAnswer(ClientCheckResult.KNOWN);
        assessPolicy.willAnswer(PolicyAssessmentResult.COVERED);
        straightThroughPayment.willAnswer(StraightThroughResult.SENT_TO_ANALYST);

        // when
        ClaimWorkerResult result = worker.process();

        // then
        assertThat(result).isEqualTo(ClaimWorkerResult.SENT_TO_ANALYST);
        assertThat(straightThroughPayment.commands()).containsExactly(new StraightThroughPaymentCommand(
                claimId, ClientCheckResult.KNOWN, PolicyAssessmentResult.COVERED, false));
    }

    @Test
    void should_pass_a_death_message_through_the_same_path() {
        // given
        ClaimId funeralId = new ClaimId(UUID.randomUUID());
        ClaimId deathId = new ClaimId(UUID.randomUUID());
        claimQueue.enqueue(new ClaimQueuedMessage(funeralId, ClaimType.FUNERAL));
        claimQueue.enqueue(new ClaimQueuedMessage(deathId, ClaimType.DEATH));
        validateClient.willAnswer(ClientCheckResult.KNOWN);
        assessPolicy.willAnswer(PolicyAssessmentResult.COVERED);
        straightThroughPayment.willAnswer(StraightThroughResult.SENT_TO_ANALYST);

        // when
        ClaimWorkerResult result = worker.process();

        // then
        assertThat(result).isEqualTo(ClaimWorkerResult.SENT_TO_ANALYST);
        assertThat(validateClient.commands()).containsExactly(new ValidateClientCommand(deathId));
        assertThat(assessPolicy.commands()).containsExactly(new AssessPolicyCommand(deathId));
        assertThat(straightThroughPayment.commands()).containsExactly(new StraightThroughPaymentCommand(
                deathId, ClientCheckResult.KNOWN, PolicyAssessmentResult.COVERED, false));
        assertThat(claimQueue.messages()).containsExactly(new ClaimQueuedMessage(funeralId, ClaimType.FUNERAL));
    }

    @Test
    void should_propagate_a_receive_failure() {
        // given
        var failure = new ClaimQueueUnavailableException("Claim queue is unavailable");
        ClaimWorkerUseCase failingWorker = new ClaimWorkerService(
                new ThrowingClaimQueue(failure),
                validateClient,
                assessPolicy,
                straightThroughPayment,
                claimWorkerResultMapper);

        // when / then
        assertThatThrownBy(failingWorker::process).isSameAs(failure);
        assertThat(validateClient.commands()).isEmpty();
        assertThat(assessPolicy.commands()).isEmpty();
        assertThat(straightThroughPayment.commands()).isEmpty();
    }

    @Test
    void should_leave_the_message_when_the_client_check_fails() {
        // given
        ClaimId claimId = enqueue(ClaimType.FUNERAL);
        var failure = new ClientRegistryUnavailableException("Client Registry is unavailable");
        validateClient.willFail(failure);

        // when / then
        assertThatThrownBy(worker::process).isSameAs(failure);
        assertThatThrownBy(worker::process).isSameAs(failure);
        assertThat(validateClient.commands())
                .containsExactly(new ValidateClientCommand(claimId), new ValidateClientCommand(claimId));
        assertThat(assessPolicy.commands()).isEmpty();
        assertThat(straightThroughPayment.commands()).isEmpty();
        assertThat(claimQueue.messages()).containsExactly(new ClaimQueuedMessage(claimId, ClaimType.FUNERAL));
    }

    @Test
    void should_propagate_a_claim_not_found_failure() {
        // given
        ClaimId claimId = enqueue(ClaimType.FUNERAL);
        var failure = new ClaimNotFoundException(claimId);
        validateClient.willFail(failure);

        // when / then
        assertThatThrownBy(worker::process).isSameAs(failure);
        assertThat(assessPolicy.commands()).isEmpty();
        assertThat(straightThroughPayment.commands()).isEmpty();
        assertThat(claimQueue.messages()).isEmpty();
    }

    @Test
    void should_propagate_a_policy_check_failure() {
        // given
        ClaimId claimId = enqueue(ClaimType.FUNERAL);
        validateClient.willAnswer(ClientCheckResult.KNOWN);
        var failure = new PolicyManagerUnavailableException("Policy Manager is unavailable");
        assessPolicy.willFail(failure);

        // when / then
        assertThatThrownBy(worker::process).isSameAs(failure);
        assertThat(straightThroughPayment.commands()).isEmpty();
        assertThat(claimQueue.messages()).containsExactly(new ClaimQueuedMessage(claimId, ClaimType.FUNERAL));
    }

    @Test
    void should_propagate_a_straight_through_failure() {
        // given
        ClaimId claimId = enqueue(ClaimType.FUNERAL);
        validateClient.willAnswer(ClientCheckResult.KNOWN);
        assessPolicy.willAnswer(PolicyAssessmentResult.COVERED);
        var failure = new PaymentUnavailableException("Payment System is unavailable");
        straightThroughPayment.willFail(failure);

        // when / then
        assertThatThrownBy(worker::process).isSameAs(failure);
        assertThat(validateClient.commands()).containsExactly(new ValidateClientCommand(claimId));
        assertThat(assessPolicy.commands()).containsExactly(new AssessPolicyCommand(claimId));
        assertThat(claimQueue.messages()).containsExactly(new ClaimQueuedMessage(claimId, ClaimType.FUNERAL));
    }

    private ClaimId enqueue(ClaimType claimType) {
        ClaimId claimId = new ClaimId(UUID.randomUUID());
        claimQueue.enqueue(new ClaimQueuedMessage(claimId, claimType));
        return claimId;
    }

    private static final class ThrowingClaimQueue implements ClaimQueuePort {

        private final RuntimeException failure;

        private ThrowingClaimQueue(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void enqueue(ClaimQueuedMessage message) {
            throw new UnsupportedOperationException("enqueue");
        }

        @Override
        public Optional<ClaimQueuedMessage> receive() {
            throw failure;
        }

        @Override
        public void acknowledge(ClaimQueuedMessage message) {
            throw new UnsupportedOperationException("acknowledge");
        }
    }

    private static final class FakeValidateClient implements ValidateClientUseCase {

        private final List<ValidateClientCommand> commands = new ArrayList<>();
        private ClientCheckResult result = ClientCheckResult.KNOWN;
        private RuntimeException failure;

        void willAnswer(ClientCheckResult result) {
            this.result = result;
            this.failure = null;
        }

        void willFail(RuntimeException failure) {
            this.failure = failure;
        }

        List<ValidateClientCommand> commands() {
            return List.copyOf(commands);
        }

        @Override
        public ClientCheckResult validate(ValidateClientCommand command) {
            commands.add(command);
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class FakeAssessPolicy implements AssessPolicyUseCase {

        private final List<AssessPolicyCommand> commands = new ArrayList<>();
        private PolicyAssessmentResult result = PolicyAssessmentResult.COVERED;
        private RuntimeException failure;

        void willAnswer(PolicyAssessmentResult result) {
            this.result = result;
            this.failure = null;
        }

        void willFail(RuntimeException failure) {
            this.failure = failure;
        }

        List<AssessPolicyCommand> commands() {
            return List.copyOf(commands);
        }

        @Override
        public PolicyAssessmentResult assess(AssessPolicyCommand command) {
            commands.add(command);
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class FakeStraightThroughPayment implements StraightThroughPaymentUseCase {

        private final List<StraightThroughPaymentCommand> commands = new ArrayList<>();
        private StraightThroughResult result = StraightThroughResult.PAYMENT_INITIATED;
        private RuntimeException failure;

        void willAnswer(StraightThroughResult result) {
            this.result = result;
            this.failure = null;
        }

        void willFail(RuntimeException failure) {
            this.failure = failure;
        }

        List<StraightThroughPaymentCommand> commands() {
            return List.copyOf(commands);
        }

        @Override
        public StraightThroughResult execute(StraightThroughPaymentCommand command) {
            commands.add(command);
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
