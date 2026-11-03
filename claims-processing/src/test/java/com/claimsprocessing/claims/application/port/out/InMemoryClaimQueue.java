package com.claimsprocessing.claims.application.port.out;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.claimsprocessing.claims.domain.ClaimType;

public final class InMemoryClaimQueue implements ClaimQueuePort {

    private final List<ClaimQueuedMessage> deathMessages = new ArrayList<>();
    private final List<ClaimQueuedMessage> normalMessages = new ArrayList<>();
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
        var waiting = new ArrayList<ClaimQueuedMessage>(deathMessages.size() + normalMessages.size());
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
    public Optional<ClaimQueuedMessage> receive() {
        if (!deathMessages.isEmpty()) {
            return Optional.of(deathMessages.removeFirst());
        }
        if (!normalMessages.isEmpty()) {
            return Optional.of(normalMessages.removeFirst());
        }
        return Optional.empty();
    }
}
