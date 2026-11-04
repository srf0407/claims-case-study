package com.claimsprocessing.claims.adapter.out.policymanager;

public record PolicyManagerResponse(String policyNumber, String status, Boolean covered) {
}
