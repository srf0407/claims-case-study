package com.claimsprocessing.claims.application.port.out;

public interface ClaimQueuePort {

    void enqueue(ClaimQueuedMessage message);
}
