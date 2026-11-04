package com.claimsprocessing.claims.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import org.junit.jupiter.api.Test;

class InMemoryClaimQueueTest {

    @Test
    void should_return_empty_when_nothing_is_queued() {
        // given
        var queue = new InMemoryClaimQueue();

        // when
        var received = queue.receive();

        // then
        assertThat(received).isEmpty();
        assertThat(queue.messages()).isEmpty();
    }

    @Test
    void should_return_a_later_death_message_before_an_earlier_normal_message() {
        // given
        var queue = new InMemoryClaimQueue();
        var funeral = message("11111111-1111-1111-1111-111111111111", ClaimType.FUNERAL);
        var death = message("22222222-2222-2222-2222-222222222222", ClaimType.DEATH);
        queue.enqueue(funeral);
        queue.enqueue(death);

        // when
        var received = queue.receive();

        // then
        assertThat(received).contains(death);
    }

    @Test
    void should_return_death_messages_in_enqueue_order() {
        // given
        var queue = new InMemoryClaimQueue();
        var first = message("11111111-1111-1111-1111-111111111111", ClaimType.DEATH);
        var second = message("22222222-2222-2222-2222-222222222222", ClaimType.DEATH);
        queue.enqueue(first);
        queue.enqueue(second);

        // when
        var firstReceived = queue.receive();
        queue.acknowledge(firstReceived.orElseThrow());
        var secondReceived = queue.receive();

        // then
        assertThat(firstReceived).contains(first);
        assertThat(secondReceived).contains(second);
    }

    @Test
    void should_return_disability_and_funeral_messages_in_enqueue_order() {
        // given
        var queue = new InMemoryClaimQueue();
        var disability = message("11111111-1111-1111-1111-111111111111", ClaimType.DISABILITY);
        var funeral = message("22222222-2222-2222-2222-222222222222", ClaimType.FUNERAL);
        queue.enqueue(disability);
        queue.enqueue(funeral);

        // when
        var firstReceived = queue.receive();
        queue.acknowledge(firstReceived.orElseThrow());
        var secondReceived = queue.receive();

        // then
        assertThat(firstReceived).contains(disability);
        assertThat(secondReceived).contains(funeral);
    }

    @Test
    void should_keep_a_message_until_it_is_acknowledged() {
        // given
        var queue = new InMemoryClaimQueue();
        var death = message("11111111-1111-1111-1111-111111111111", ClaimType.DEATH);
        queue.enqueue(death);

        // when
        var received = queue.receive();

        // then
        assertThat(received).contains(death);
        assertThat(queue.messages()).containsExactly(death);
        assertThat(queue.receive()).contains(death);
        queue.acknowledge(death);
        assertThat(queue.messages()).isEmpty();
        assertThat(queue.receive()).isEmpty();
    }

    @Test
    void should_list_waiting_messages_with_death_ahead_of_normal() {
        // given
        var queue = new InMemoryClaimQueue();
        var funeral = message("11111111-1111-1111-1111-111111111111", ClaimType.FUNERAL);
        var death = message("22222222-2222-2222-2222-222222222222", ClaimType.DEATH);
        var disability = message("33333333-3333-3333-3333-333333333333", ClaimType.DISABILITY);
        queue.enqueue(funeral);
        queue.enqueue(death);
        queue.enqueue(disability);

        // when
        var waiting = queue.messages();

        // then
        assertThat(waiting).containsExactly(death, funeral, disability);
    }

    @Test
    void should_reject_a_null_message() {
        // given
        var queue = new InMemoryClaimQueue();

        // when / then
        assertThatThrownBy(() -> queue.enqueue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("message");
        assertThat(queue.messages()).isEmpty();
        assertThat(queue.receive()).isEmpty();
    }

    @Test
    void should_return_a_waiting_message_when_enqueue_is_set_to_fail() {
        // given
        var queue = new InMemoryClaimQueue();
        var death = message("11111111-1111-1111-1111-111111111111", ClaimType.DEATH);
        queue.enqueue(death);
        queue.willFail(new ClaimQueueUnavailableException("Claim queue is unavailable"));

        // when
        var received = queue.receive();

        // then
        assertThat(received).contains(death);
    }

    private static ClaimQueuedMessage message(String id, ClaimType claimType) {
        return new ClaimQueuedMessage(new ClaimId(UUID.fromString(id)), claimType);
    }
}
