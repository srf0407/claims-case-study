package com.claimsprocessing.claims.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimAccepted;
import com.claimsprocessing.claims.domain.ClaimEvent;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimStatusChanged;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.RejectionReason;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public final class ClaimItemMapper {

    private static final String ACCEPTED = "ACCEPTED";
    private static final String STATUS_CHANGED = "STATUS_CHANGED";

    public Map<String, AttributeValue> toAttributes(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        return attributes(toItem(claim));
    }

    public Claim toClaim(Map<String, AttributeValue> attributes) {
        Objects.requireNonNull(attributes, "attributes");
        return toClaim(item(attributes));
    }

    private ClaimItem toItem(Claim claim) {
        return new ClaimItem(
                claim.id().value().toString(),
                claim.clientId(),
                claim.policyNumber(),
                claim.claimType().name(),
                claim.amount().toPlainString(),
                claim.incidentDate().toString(),
                claim.dateOfDeath() == null ? null : claim.dateOfDeath().toString(),
                claim.deathCertificateRef(),
                claim.status().name(),
                claim.rejectionReason().map(Enum::name).orElse(null),
                claim.events().stream().map(this::eventItem).toList());
    }

    private Claim toClaim(ClaimItem item) {
        return Claim.restore(
                new ClaimId(UUID.fromString(item.id())),
                item.clientId(),
                item.policyNumber(),
                ClaimType.valueOf(item.claimType()),
                new BigDecimal(item.amount()),
                LocalDate.parse(item.incidentDate()),
                item.dateOfDeath() == null ? null : LocalDate.parse(item.dateOfDeath()),
                item.deathCertificateRef(),
                ClaimStatus.valueOf(item.status()),
                rejectionReason(item.rejectionReason()),
                item.events().stream().map(this::event).toList());
    }

    private Map<String, AttributeValue> attributes(ClaimItem item) {
        Map<String, AttributeValue> attributes = new LinkedHashMap<>();
        put(attributes, "id", item.id());
        put(attributes, "clientId", item.clientId());
        put(attributes, "policyNumber", item.policyNumber());
        put(attributes, "claimType", item.claimType());
        put(attributes, "amount", item.amount());
        put(attributes, "incidentDate", item.incidentDate());
        put(attributes, "dateOfDeath", item.dateOfDeath());
        put(attributes, "deathCertificateRef", item.deathCertificateRef());
        put(attributes, "status", item.status());
        put(attributes, "rejectionReason", item.rejectionReason());
        attributes.put(
                "events",
                AttributeValue.builder()
                        .l(item.events().stream().map(this::eventValue).toList())
                        .build());
        return attributes;
    }

    private ClaimItem item(Map<String, AttributeValue> attributes) {
        return new ClaimItem(
                required(attributes, "id"),
                required(attributes, "clientId"),
                required(attributes, "policyNumber"),
                required(attributes, "claimType"),
                required(attributes, "amount"),
                required(attributes, "incidentDate"),
                optional(attributes, "dateOfDeath"),
                optional(attributes, "deathCertificateRef"),
                required(attributes, "status"),
                optional(attributes, "rejectionReason"),
                eventItems(attributes.get("events")));
    }

    private ClaimEventItem eventItem(ClaimEvent event) {
        return switch (event) {
            case ClaimAccepted accepted ->
                new ClaimEventItem(ACCEPTED, accepted.occurredAt().toString(), null, null, null);
            case ClaimStatusChanged changed -> new ClaimEventItem(
                    STATUS_CHANGED,
                    changed.occurredAt().toString(),
                    changed.from().name(),
                    changed.to().name(),
                    changed.reason().map(Enum::name).orElse(null));
        };
    }

    private ClaimEvent event(ClaimEventItem item) {
        Instant occurredAt = Instant.parse(item.occurredAt());
        return switch (item.type()) {
            case ACCEPTED -> new ClaimAccepted(occurredAt);
            case STATUS_CHANGED -> new ClaimStatusChanged(
                    ClaimStatus.valueOf(item.from()),
                    ClaimStatus.valueOf(item.to()),
                    rejectionReason(item.reason()),
                    occurredAt);
            default -> throw new IllegalArgumentException("event type");
        };
    }

    private AttributeValue eventValue(ClaimEventItem event) {
        Map<String, AttributeValue> map = new LinkedHashMap<>();
        put(map, "type", event.type());
        put(map, "occurredAt", event.occurredAt());
        put(map, "from", event.from());
        put(map, "to", event.to());
        put(map, "reason", event.reason());
        return AttributeValue.builder().m(map).build();
    }

    private List<ClaimEventItem> eventItems(AttributeValue value) {
        if (value == null || value.l() == null) {
            return List.of();
        }
        return value.l().stream()
                .map(attribute -> new ClaimEventItem(
                        string(attribute.m(), "type"),
                        string(attribute.m(), "occurredAt"),
                        string(attribute.m(), "from"),
                        string(attribute.m(), "to"),
                        string(attribute.m(), "reason")))
                .toList();
    }

    private static Optional<RejectionReason> rejectionReason(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(RejectionReason.valueOf(value));
    }

    private static void put(Map<String, AttributeValue> attributes, String name, String value) {
        if (value != null) {
            attributes.put(name, AttributeValue.builder().s(value).build());
        }
    }

    private static String required(Map<String, AttributeValue> attributes, String name) {
        String value = optional(attributes, name);
        if (value == null) {
            throw new IllegalArgumentException(name);
        }
        return value;
    }

    private static String optional(Map<String, AttributeValue> attributes, String name) {
        return string(attributes, name);
    }

    private static String string(Map<String, AttributeValue> attributes, String name) {
        AttributeValue value = attributes.get(name);
        if (value == null) {
            return null;
        }
        return value.s();
    }
}
