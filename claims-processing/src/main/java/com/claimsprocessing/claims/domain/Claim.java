package com.claimsprocessing.claims.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Claim {

    private final ClaimId id;
    private final String clientId;
    private final String policyNumber;
    private final ClaimType claimType;
    private final BigDecimal amount;
    private final LocalDate incidentDate;
    private final LocalDate dateOfDeath;
    private final String deathCertificateRef;
    private final ClaimStatus status;
    private final List<ClaimEvent> events;

    private Claim(
            ClaimId id,
            String clientId,
            String policyNumber,
            ClaimType claimType,
            BigDecimal amount,
            LocalDate incidentDate,
            LocalDate dateOfDeath,
            String deathCertificateRef,
            ClaimStatus status,
            List<ClaimEvent> events) {
        this.id = id;
        this.clientId = clientId;
        this.policyNumber = policyNumber;
        this.claimType = claimType;
        this.amount = amount;
        this.incidentDate = incidentDate;
        this.dateOfDeath = dateOfDeath;
        this.deathCertificateRef = deathCertificateRef;
        this.status = status;
        this.events = events;
    }

    public static Claim accept(
            ClaimType claimType,
            String clientId,
            String policyNumber,
            BigDecimal amount,
            LocalDate incidentDate,
            LocalDate dateOfDeath,
            String deathCertificateRef,
            Clock clock) {
        Objects.requireNonNull(clock, "clock");
        validate(claimType, clientId, policyNumber, amount, incidentDate, dateOfDeath, deathCertificateRef);
        return new Claim(
                new ClaimId(UUID.randomUUID()),
                clientId,
                policyNumber,
                claimType,
                amount,
                incidentDate,
                dateOfDeath,
                deathCertificateRef,
                ClaimStatus.ACCEPTED,
                List.copyOf(List.of(new ClaimAccepted(clock.instant()))));
    }

    public Claim transitionTo(ClaimStatus next, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(next, "status");
        if (!status.allows(next)) {
            throw new IllegalClaimTransitionException(status, next);
        }
        var updatedEvents = new ArrayList<ClaimEvent>(events);
        updatedEvents.add(new ClaimStatusChanged(status, next, clock.instant()));
        return new Claim(
                id,
                clientId,
                policyNumber,
                claimType,
                amount,
                incidentDate,
                dateOfDeath,
                deathCertificateRef,
                next,
                List.copyOf(updatedEvents));
    }

    private static void validate(
            ClaimType claimType,
            String clientId,
            String policyNumber,
            BigDecimal amount,
            LocalDate incidentDate,
            LocalDate dateOfDeath,
            String deathCertificateRef) {
        requireText(clientId, "clientId");
        requireText(policyNumber, "policyNumber");
        if (claimType == null) {
            throw new InvalidClaimException("claimType is required");
        }
        if (amount == null) {
            throw new InvalidClaimException("amount is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidClaimException("amount must be greater than zero");
        }
        if (incidentDate == null) {
            throw new InvalidClaimException("incidentDate is required");
        }
        if (claimType == ClaimType.DEATH) {
            if (dateOfDeath == null) {
                throw new InvalidClaimException("dateOfDeath is required for a death claim");
            }
            requireText(deathCertificateRef, "deathCertificateRef");
            return;
        }
        if (dateOfDeath != null || deathCertificateRef != null) {
            throw new InvalidClaimException("death fields are only allowed for a death claim");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidClaimException(name + " is required");
        }
    }

    public ClaimId id() {
        return id;
    }

    public String clientId() {
        return clientId;
    }

    public String policyNumber() {
        return policyNumber;
    }

    public ClaimType claimType() {
        return claimType;
    }

    public BigDecimal amount() {
        return amount;
    }

    public LocalDate incidentDate() {
        return incidentDate;
    }

    public LocalDate dateOfDeath() {
        return dateOfDeath;
    }

    public String deathCertificateRef() {
        return deathCertificateRef;
    }

    public ClaimStatus status() {
        return status;
    }

    public List<ClaimEvent> events() {
        return events;
    }
}
