package com.claimsprocessing.claims.application.port.out;

import java.util.Optional;

/**
 * One port over the normal queue and the death-claim queue. The implementation routes a
 * {@code DEATH} message to the death queue and every other claim type to the normal queue.
 * {@link #receive()} returns a waiting death message before any normal message, first in first out
 * within each queue. The message stays in flight until {@link #acknowledge(ClaimQueuedMessage)}
 * or {@link #release(ClaimQueuedMessage)}. A later receive returns an in-flight message before it
 * reads either queue. When both queues are empty and nothing is in flight it returns
 * {@link Optional#empty()}.
 */
public interface ClaimQueuePort {

    void enqueue(ClaimQueuedMessage message);

    Optional<ClaimQueuedMessage> receive();

    void acknowledge(ClaimQueuedMessage message);

    /**
     * Drops the in-flight hold without deleting the message, so the next receive can take a
     * death claim ahead of it. The in-memory queues put the message back at the head of its queue.
     * SQS leaves the message invisible until the visibility timeout.
     */
    void release(ClaimQueuedMessage message);
}
