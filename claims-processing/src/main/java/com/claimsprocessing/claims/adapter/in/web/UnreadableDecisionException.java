package com.claimsprocessing.claims.adapter.in.web;

final class UnreadableDecisionException extends RuntimeException {

    UnreadableDecisionException() {
        super("decision is required");
    }
}
