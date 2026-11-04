package com.claimsprocessing.claims.adapter.out.persistence;

public record ClaimEventItem(String type, String occurredAt, String from, String to, String reason) {
}
