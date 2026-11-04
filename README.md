# Claims case study

`claims-processing` accepts an insurance claim, checks the client and the policy, and initiates payment. Death claims are taken from the queue first. The design and the flow are in [claims-processing/docs/solution.md](claims-processing/docs/solution.md).

## Project structure

```text
claims-processing/
├── src/main/java/com/claimsprocessing/claims/domain/        Claim rules and statuses. No Spring.
├── src/main/java/com/claimsprocessing/claims/application/   Use cases and ports.
├── src/main/java/com/claimsprocessing/claims/adapter/      REST, schedulers, and outbound adapters.
├── src/main/java/com/claimsprocessing/claims/config/       Spring wiring.
├── docs/adr/          ADR-0001 to ADR-0004.
├── docs/diagrams/     Context diagram and claim states.
├── infra/localstack/  Creates the three SQS queues and the claims table.
├── infra/wiremock/    Stubs for the Client Registry, Policy Manager, and Payment.
└── scripts/           demo.ps1.
```

Dependencies point inward: `config` to `adapter` to `application` to `domain`. `ArchitectureTest` enforces that rule.

## Prerequisites

Java 21. Docker is required for Compose and for the LocalStack tests. The Maven wrapper is in `claims-processing/`.

## Run

From `claims-processing/`:

```text
docker compose up -d --build --wait
.\scripts\demo.ps1
docker compose down
```

The demo submits a funeral claim and, after a signed callback, prints `PAID`. It submits a death claim, approves it, and prints `PAYMENT_PENDING` (it does not send a callback for that claim). It submits client `C-404` and prints `REJECTED` and `UNKNOWN_CLIENT`.

To run the default in-memory profile, set `PAYMENT_CALLBACK_SECRET`, then start the app with `./mvnw spring-boot:run` or `.\mvnw.cmd spring-boot:run`. The client, policy, and payment base URLs are `localhost:8081`, `localhost:8082`, and `localhost:8083`, so this start does not reach `PAID` on its own. Use Compose for the full flow.

## Test

From `claims-processing/`:

```text
./mvnw verify
./mvnw -P localstack-it verify
```

`./mvnw verify` does not need Docker. The LocalStack profile does. On PowerShell the wrapper is `.\mvnw.cmd`.

## API

| Method and path | What it does |
|---|---|
| `POST /claims` | Accept a claim. Returns 201 and `claimId`. Invalid input is 400 and is not queued. |
| `GET /claims/{id}` | Return the stored claim, including status. |
| `POST /claims/{id}/decision` | Approve or reject a claim that is `AWAITING_ANALYST`. Approve starts payment. |
| `POST /payments/callback` | Apply a signed payment outcome. |

## Docs

- [Solution](claims-processing/docs/solution.md)
- [ADR-0001](claims-processing/docs/adr/0001-single-service-outbound-ports.md)
- [ADR-0002](claims-processing/docs/adr/0002-async-https-payment-webhook.md)
- [ADR-0003](claims-processing/docs/adr/0003-prioritise-death-claims-separate-queue.md)
- [ADR-0004](claims-processing/docs/adr/0004-death-claim-sla-alarms-through-an-outbound-port.md)
- [Context diagram](claims-processing/docs/diagrams/context.md)
- [Claim states](claims-processing/docs/diagrams/state-claim.md)
- [AI usage](claims-processing/docs/ai-usage.md)
