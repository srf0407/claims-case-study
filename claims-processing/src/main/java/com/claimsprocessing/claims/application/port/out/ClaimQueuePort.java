package com.claimsprocessing.claims.application.port.out;

import java.util.Optional;

/**
 * One port over the normal queue and the death-claim queue. The implementation routes a
 * {@code DEATH} message to the death queue and every other claim type to the normal queue.
 * {@link #receive()} returns a waiting death message before any normal message, first in first out
 * within each queue. The message stays in flight until {@link #acknowledge(ClaimQueuedMessage)},
 * and a later receive returns that same message. When both queues are empty and nothing is in
 * flight it returns {@link Optional#empty()}.
 */
public interface ClaimQueuePort {

    void enqueue(ClaimQueuedMessage message);

    Optional<ClaimQueuedMessage> receive();

    void acknowledge(ClaimQueuedMessage message);
}
