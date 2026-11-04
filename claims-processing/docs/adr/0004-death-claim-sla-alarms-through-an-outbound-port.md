# 0004. Death-claim SLA alarms through an outbound port

Status: Accepted
Date: 2026-11-04 · Decided by: The developer

## Context

ADR-0003 sets the death-claim SLA: 4 clock hours from accept (`ClaimAccepted.occurredAt`) until the analyst decision, or until rejection with no analyst decision. The clock runs only while the claim is `ACCEPTED` or `AWAITING_ANALYST`. It stops at `APPROVED` or `REJECTED`. A warning fires at 75% of the duration, a breach at the duration. Both numbers are config. ADR-0003 maps the alarms to CloudWatch and adds no port.

ADR-0001 sends every call out of a use case through an outbound port. This sample must not call CloudWatch. `state-claim.md` still says the clock keeps running when a death claim sits in `PAYMENT_PENDING` after payment-status retries are exhausted. That sentence stays on the `solution.md` Open table. This ADR follows ADR-0003.

## Options

1. **Rule on the claim, memory in the application.** `Claim` evaluates the level from an `Instant`, the duration, and the warning fraction. The use case reads a `Clock`, passes that instant in, scans open death claims, and emits through `DeathClaimSlaAlarmPort`. An in-memory ledger in the application remembers which warning and which breach were already raised. The logging adapter only logs.
2. **Rule on the claim, memory in the logging adapter.** Same evaluation and port. The adapter keeps the set and, on a breach, also marks the warning raised.
3. **Rule in the use case.** `Claim` stays unchanged. The service computes the level. The ledger and the port match option 1.

## Decision

Option 1. The time an open death claim has waited is a claim rule, and the caller passes the current instant in. The record of an alarm already raised stays off the claim and off the logging adapter.

## Consequences

- `Claim.openDeathSlaLevel(Instant now, Duration duration, double warningFraction)` returns `ON_TIME`, `WARNING`, or `BREACH`. The domain does not take a `Clock`. `elapsed >= duration` is a breach; otherwise `elapsed >= duration * warning-fraction` is a warning. An exact hit counts. A claim that is not an open death claim, or has no `ClaimAccepted`, throws `InvalidClaimException`.
- `CheckDeathClaimSlaService` reads the `Clock` and calls the port. The first time a claim is already past both thresholds, it emits the breach only. Recording the breach also records the warning, so a later check does not emit the warning.
- `RaisedDeathClaimSlaAlarms` is one in-memory set created in `config`. A restart can raise the same alarm again. Production dedupe stays in CloudWatch.
- The sample adapter logs a warning at WARN and a breach at ERROR, with the claim id and the elapsed time. No Micrometer and no CloudWatch client.
- `claims.death-sla.duration` defaults to `PT4H`, `warning-fraction` to `0.75`, and `check-interval` to `PT1M`. Startup rejects a non-positive duration, a fraction that is not strictly between 0 and 1, and a non-positive check interval. A scheduled adapter calls the use case on that interval. Tests set check-interval high so the scheduler does not fire during `@SpringBootTest` runs.
- `ClaimRepositoryPort.findOpenDeathClaims()` returns death claims in `ACCEPTED` or `AWAITING_ANALYST`. Every `ClaimRepositoryPort` adapter implements it. The in-memory repository filters its map; a DynamoDB repository (separate branch) would use a filtered Scan, which is fine at sample scale.
- AWS mapping (docs only), unchanged from ADR-0003: CloudWatch alarms at 75% and at the SLA. Metrics are a production option. The Claims System UI still does not show the warning.
