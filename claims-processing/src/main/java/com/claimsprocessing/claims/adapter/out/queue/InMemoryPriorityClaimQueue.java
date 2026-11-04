package com.claimsprocessing.claims.adapter.out.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.claimsprocessing.claims.application.port.out.ClaimQueuePort;
import com.claimsprocessing.claims.application.port.out.ClaimQueuedMessage;
import com.claimsprocessing.claims.domain.ClaimType;

public final class InMemoryPriorityClaimQueue implements ClaimQueuePort {

    private final List<ClaimQueuedMessage> deathMessages = new ArrayList<>();
    private final List<ClaimQueuedMessage> normalMessages = new ArrayList<>();
    private ClaimQueuedMessage inflight;

    @Override
    public synchronized void enqueue(ClaimQueuedMessage message) {
        Objects.requireNonNull(message, "message");
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

    @Override
    public synchronized void release(ClaimQueuedMessage message) {
        Objects.requireNonNull(message, "message");
        if (inflight == null) {
            return;
        }
        if (!inflight.equals(message)) {
            throw new IllegalStateException("Claim queue message is not in flight");
        }
        inflight = null;
        requeue(message);
    }

    private void requeue(ClaimQueuedMessage message) {
        if (message.claimType() == ClaimType.DEATH) {
            deathMessages.addFirst(message);
            return;
        }
        normalMessages.addFirst(message);
    }
}
