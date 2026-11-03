package com.claimsprocessing.claims.application.port.out;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InMemoryClaimQueue implements ClaimQueuePort {

    private final List<ClaimQueuedMessage> messages = new ArrayList<>();
    private ClaimQueueUnavailableException failure;
    private int failuresLeft;

    public void willFail(ClaimQueueUnavailableException failure) {
        this.failure = Objects.requireNonNull(failure, "failure");
        this.failuresLeft = Integer.MAX_VALUE;
    }

    public void failOnce(ClaimQueueUnavailableException failure) {
        this.failure = Objects.requireNonNull(failure, "failure");
        this.failuresLeft = 1;
    }

    public List<ClaimQueuedMessage> messages() {
        return List.copyOf(messages);
    }

    @Override
    public void enqueue(ClaimQueuedMessage message) {
        Objects.requireNonNull(message, "message");
        if (failuresLeft > 0) {
            failuresLeft--;
            throw failure;
        }
        messages.add(message);
    }
}
