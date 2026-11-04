package com.claimsprocessing.claims.application.port.out;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.claimsprocessing.claims.domain.ClaimType;

public final class InMemoryClaimQueue implements ClaimQueuePort {

    private final List<ClaimQueuedMessage> deathMessages = new ArrayList<>();
    private final List<ClaimQueuedMessage> normalMessages = new ArrayList<>();
    private ClaimQueuedMessage inflight;
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

    public synchronized List<ClaimQueuedMessage> messages() {
        var waiting = new ArrayList<ClaimQueuedMessage>(deathMessages.size() + normalMessages.size() + 1);
        if (inflight != null) {
            waiting.add(inflight);
        }
        waiting.addAll(deathMessages);
        waiting.addAll(normalMessages);
        return List.copyOf(waiting);
    }

    @Override
    public void enqueue(ClaimQueuedMessage message) {
        Objects.requireNonNull(message, "message");
        if (failuresLeft > 0) {
            failuresLeft--;
            throw failure;
        }
        if (message.claimType() == ClaimType.DEATH) {
            deathMessages.add(message);
        } else {
            normalMessages.add(message);
        }
    }

    @Override
    public synchronized Optional<ClaimQueuedMessage> receive() {
        if (inflight != null) {
            return Optional.of(inflight);
        }
        if (!deathMessages.isEmpty()) {
            inflight = deathMessages.removeFirst();
            return Optional.of(inflight);
        }
        if (!normalMessages.isEmpty()) {
            inflight = normalMessages.removeFirst();
            return Optional.of(inflight);
        }
        return Optional.empty();
    }

    @Override
    public synchronized void acknowledge(ClaimQueuedMessage message) {
        Objects.requireNonNull(message, "message");
        if (inflight == null) {
            return;
        }
        if (!inflight.equals(message)) {
            throw new IllegalStateException("Claim queue message is not in flight");
        }
        inflight = null;
    }
}
