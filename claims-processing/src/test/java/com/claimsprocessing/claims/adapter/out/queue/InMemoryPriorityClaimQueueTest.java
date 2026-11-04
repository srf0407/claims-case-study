package com.claimsprocessing.claims.adapter.out.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.claimsprocessing.claims.application.port.out.ClaimQueuedMessage;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import org.junit.jupiter.api.Test;

class InMemoryPriorityClaimQueueTest {

    @Test
    void should_return_empty_when_nothing_is_queued() {
        // given
        var queue = new InMemoryPriorityClaimQueue();

        // when
        var received = queue.receive();

        // then
        assertThat(received).isEmpty();
    }

    @Test
    void should_return_a_later_death_message_before_an_earlier_normal_message() {
        // given
        var queue = new InMemoryPriorityClaimQueue();
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
        var queue = new InMemoryPriorityClaimQueue();
        var first = message("11111111-1111-1111-1111-111111111111", ClaimType.DEATH);
        var second = message("22222222-2222-2222-2222-222222222222", ClaimType.DEATH);
        queue.enqueue(first);
        queue.enqueue(second);

        // when
        var firstReceived = queue.receive();
        var secondReceived = queue.receive();

        // then
        assertThat(firstReceived).contains(first);
        assertThat(secondReceived).contains(second);
    }

    @Test
    void should_return_normal_messages_in_enqueue_order() {
        // given
        var queue = new InMemoryPriorityClaimQueue();
        var disability = message("11111111-1111-1111-1111-111111111111", ClaimType.DISABILITY);
        var funeral = message("22222222-2222-2222-2222-222222222222", ClaimType.FUNERAL);
        queue.enqueue(disability);
        queue.enqueue(funeral);

        // when
        var firstReceived = queue.receive();
        var secondReceived = queue.receive();

        // then
        assertThat(firstReceived).contains(disability);
        assertThat(secondReceived).contains(funeral);
    }

    private static ClaimQueuedMessage message(String id, ClaimType claimType) {
        return new ClaimQueuedMessage(new ClaimId(UUID.fromString(id)), claimType);
    }
}
