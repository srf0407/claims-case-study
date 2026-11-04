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
        if (!deathMessages.isEmpty()) {
            return Optional.of(deathMessages.removeFirst());
        }
        if (!normalMessages.isEmpty()) {
            return Optional.of(normalMessages.removeFirst());
        }
        return Optional.empty();
    }
}
