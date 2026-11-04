package com.claimsprocessing.claims.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private final Optional<RejectionReason> rejectionReason;
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
            Optional<RejectionReason> rejectionReason,
            List<ClaimEvent> events) {
        Objects.requireNonNull(rejectionReason, "rejectionReason");
        if (status == ClaimStatus.REJECTED && rejectionReason.isEmpty()) {
            throw new InvalidClaimException("rejection reason is required");
        }
        if (status != ClaimStatus.REJECTED && rejectionReason.isPresent()) {
            throw new InvalidClaimException("rejection reason is only allowed when rejected");
        }
        this.id = id;
        this.clientId = clientId;
        this.policyNumber = policyNumber;
        this.claimType = claimType;
        this.amount = amount;
        this.incidentDate = incidentDate;
        this.dateOfDeath = dateOfDeath;
        this.deathCertificateRef = deathCertificateRef;
        this.status = status;
        this.rejectionReason = rejectionReason;
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
                Optional.empty(),
                List.copyOf(List.of(new ClaimAccepted(clock.instant()))));
    }

    public static Claim restore(
            ClaimId id,
            String clientId,
            String policyNumber,
            ClaimType claimType,
            BigDecimal amount,
            LocalDate incidentDate,
            LocalDate dateOfDeath,
            String deathCertificateRef,
            ClaimStatus status,
            Optional<RejectionReason> rejectionReason,
            List<ClaimEvent> events) {
        if (id == null) {
            throw new InvalidClaimException("Claim id is required");
        }
        if (status == null) {
            throw new InvalidClaimException("status is required");
        }
        validate(claimType, clientId, policyNumber, amount, incidentDate, dateOfDeath, deathCertificateRef);
        if (events == null || events.isEmpty()) {
            throw new InvalidClaimException("events are required");
        }
        return new Claim(
                id,
                clientId,
                policyNumber,
                claimType,
                amount,
                incidentDate,
                dateOfDeath,
                deathCertificateRef,
                status,
                rejectionReason,
                List.copyOf(events));
    }

    public Claim transitionTo(ClaimStatus next, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(next, "status");
        if (!status.allows(next)) {
            throw new IllegalClaimTransitionException(status, next);
        }
        var updatedEvents = new ArrayList<ClaimEvent>(events);
        updatedEvents.add(new ClaimStatusChanged(status, next, Optional.empty(), clock.instant()));
        return copyWith(next, rejectionReason, List.copyOf(updatedEvents));
    }

    public Claim reject(RejectionReason reason, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(reason, "reason");
        if (!canRejectFrom(status)) {
            throw new IllegalClaimTransitionException(status, ClaimStatus.REJECTED);
        }
        if (!reasonMatches(status, reason)) {
            throw new IllegalClaimTransitionException(status, reason);
        }
        var updatedEvents = new ArrayList<ClaimEvent>(events);
        updatedEvents.add(new ClaimStatusChanged(
                status, ClaimStatus.REJECTED, Optional.of(reason), clock.instant()));
        return copyWith(ClaimStatus.REJECTED, Optional.of(reason), List.copyOf(updatedEvents));
    }

    public boolean isEligibleForStraightThrough(BigDecimal limit, boolean flagged) {
        Objects.requireNonNull(limit, "limit");
        return claimType != ClaimType.DEATH && !flagged && amount.compareTo(limit) < 0;
    }

    public DeathSlaLevel openDeathSlaLevel(Instant now, Duration duration, double warningFraction) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(duration, "duration");
        if (claimType != ClaimType.DEATH || !openForDeathSla(status)) {
            throw new InvalidClaimException("open death claim is required");
        }
        Instant acceptedAt = events.stream()
                .filter(ClaimAccepted.class::isInstance)
                .map(ClaimAccepted.class::cast)
                .map(ClaimAccepted::occurredAt)
                .findFirst()
                .orElseThrow(() -> new InvalidClaimException("accept time is required"));
        Duration elapsed = Duration.between(acceptedAt, now);
        if (elapsed.compareTo(duration) >= 0) {
            return DeathSlaLevel.BREACH;
        }
        Duration warningAfter = Duration.ofNanos(Math.round(duration.toNanos() * warningFraction));
        if (elapsed.compareTo(warningAfter) >= 0) {
            return DeathSlaLevel.WARNING;
        }
        return DeathSlaLevel.ON_TIME;
    }

    private Claim copyWith(ClaimStatus status, Optional<RejectionReason> rejectionReason, List<ClaimEvent> events) {
        return new Claim(
                id,
                clientId,
                policyNumber,
                claimType,
                amount,
                incidentDate,
                dateOfDeath,
                deathCertificateRef,
                status,
                rejectionReason,
                events);
    }

    private static boolean openForDeathSla(ClaimStatus status) {
        return status == ClaimStatus.ACCEPTED || status == ClaimStatus.AWAITING_ANALYST;
    }

    private static boolean canRejectFrom(ClaimStatus status) {
        return status == ClaimStatus.ACCEPTED || status == ClaimStatus.AWAITING_ANALYST;
    }

    private static boolean reasonMatches(ClaimStatus status, RejectionReason reason) {
        return switch (status) {
            case ACCEPTED -> reason == RejectionReason.UNKNOWN_CLIENT
                    || reason == RejectionReason.POLICY_LAPSED
                    || reason == RejectionReason.NO_COVER;
            case AWAITING_ANALYST -> reason == RejectionReason.ANALYST_REJECTED;
            default -> false;
        };
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

    public Optional<RejectionReason> rejectionReason() {
        return rejectionReason;
    }

    public List<ClaimEvent> events() {
        return events;
    }
}
