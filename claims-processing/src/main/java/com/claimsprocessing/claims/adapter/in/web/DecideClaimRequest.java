package com.claimsprocessing.claims.adapter.in.web;

public record DecideClaimRequest(Decision decision) {

    public enum Decision {
        APPROVE,
        REJECT
    }
}
