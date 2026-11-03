package com.claimsprocessing.claims.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

final class ClaimBuilder {

    private ClaimType claimType = ClaimType.FUNERAL;
    private String clientId = "client-1";
    private String policyNumber = "POL-1";
    private BigDecimal amount = new BigDecimal("10.50");
    private LocalDate incidentDate = LocalDate.of(2026, 1, 15);
    private LocalDate dateOfDeath;
    private String deathCertificateRef;
    private Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    static ClaimBuilder aClaim() {
        return new ClaimBuilder();
    }

    ClaimBuilder death() {
        claimType = ClaimType.DEATH;
        dateOfDeath = LocalDate.of(2026, 1, 10);
        deathCertificateRef = "DC-1";
        return this;
    }

    ClaimBuilder claimType(ClaimType claimType) {
        this.claimType = claimType;
        return this;
    }

    ClaimBuilder clientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    ClaimBuilder policyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
        return this;
    }

    ClaimBuilder amount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    ClaimBuilder incidentDate(LocalDate incidentDate) {
        this.incidentDate = incidentDate;
        return this;
    }

    ClaimBuilder dateOfDeath(LocalDate dateOfDeath) {
        this.dateOfDeath = dateOfDeath;
        return this;
    }

    ClaimBuilder deathCertificateRef(String deathCertificateRef) {
        this.deathCertificateRef = deathCertificateRef;
        return this;
    }

    ClaimBuilder clock(Clock clock) {
        this.clock = clock;
        return this;
    }

    Claim build() {
        return Claim.accept(
                claimType,
                clientId,
                policyNumber,
                amount,
                incidentDate,
                dateOfDeath,
                deathCertificateRef,
                clock);
    }
}
