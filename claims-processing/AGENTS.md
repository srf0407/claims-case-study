# AGENTS.md: claims-service

## Golden rule

**Never assume.** If anything is unclear, ambiguous, or missing from the docs, stop and ask.
Propose options with trade-offs; the human makes every decision. Ask for feedback after each step.

## Project

`claims-service` is an insurance claims-processing service built for a developer case study. A web
form posts claim JSON to it. It validates the client with the **Client Registry**, checks
policy/plan/benefits with the **Policy Manager**, and initiates payment with the **Payment System**,
which later reports the payment status back. These external systems are reached only through
outbound ports and are mocked with WireMock in tests. **Death claims are time-sensitive and get SLA
priority.** Storage is in-memory. AWS appears in the design docs (`docs/`). A LocalStack integration (local AWS emulation)
may be added later if time allows; do not add AWS dependencies or LocalStack setup unless asked. There is no frontend.

Stack: Java 21, Maven (single module), Spring Boot (being added), JUnit, ArchUnit, WireMock.
Base package: `com.claimsprocessing.claims`.

## Commands

- `./mvnw verify`: full build plus all tests, including the ArchUnit gate. Run it before saying a task is done.
- `./mvnw test`: tests only.
- `./mvnw test -Dtest=ArchitectureTest`: architecture gate only.
- Do not add a task log, or any new rows, to `docs/ai-usage.md`. That file is the finished case-study write-up. Later pull requests must not append an AI log to it. There is no logging script.
- ADRs: use the `architecture-decision-record` skill (no script).

## Layer map (dependencies point inward)

```
config ──► adapter ──► application ──► domain
```

| Package | Holds | Allowed to depend on |
|---|---|---|
| `domain` | Entities, value objects, enums, domain exceptions. Pure Java: **no Spring, Jakarta or Jackson**. | JDK only |
| `application` | Use cases plus `port/in` (use-case interfaces) and `port/out` (ports to external systems/storage). Plain Java, **no Spring annotations**. | `domain` |
| `adapter/in/web` | REST controllers plus request/response DTOs (records). Maps DTOs to commands and calls `port/in`. | `application`, `domain` |
| `adapter/out/<system>` | `clientregistry`, `policymanager`, `payment`, `persistence`: client, DTOs, mapper. | `application`, `domain` |
| `config` | Spring wiring. Creates the use-case beans (`@Bean`), RestClients, properties. | everything |

## Placement rules

- Business rules (eligibility, death-claim priority, status transitions) live in `domain`; orchestration lives in application services.
- External DTOs **never leave their adapter**; `XxxMapper` converts to and from domain types.
- Adapters never depend on each other (`adapter/in` never touches `adapter/out`; `adapter/out/*` are mutually independent).
- Every external failure (4xx/5xx/timeout) is translated into a **domain exception inside its adapter**; no HTTP or Spring exception crosses into application.
- Constructor injection only; no `@Autowired` fields. Prefer `final` fields.
- Use records for DTOs and value objects.
- Validate input at the boundary (web DTO) and enforce invariants in the domain.

## Naming

| Kind | Name | Package |
|---|---|---|
| Inbound port | `SubmitClaimUseCase` (interface) | `application.port.in` |
| Use-case implementation | `SubmitClaimService` | `application` |
| Outbound port | `PolicyManagerPort`, `ClaimRepositoryPort` | `application.port.out` |
| External client | `PolicyManagerClient` (implements the port) | `adapter.out.policymanager` |
| Mapper | `PolicyManagerMapper` | same adapter package |
| Controller | `ClaimController` | `adapter.in.web` |

Tests mirror the main package layout; architecture tests are in `src/test/java/com/claimsprocessing/claims/architecture/`.

## Rules for agents

1. `src/test/java/com/claimsprocessing/claims/architecture/ArchitectureTest.java` is the **hard gate**. Never weaken, delete, `@ArchIgnore` or loosen an ArchUnit rule to make a change pass; fix the code instead. Change a rule only when asked explicitly, and record the change in an ADR.
2. Run `./mvnw verify` before saying a task is done, and report the result.
3. Use the skills in `.agents/skills/`; agents load them by name. Workflow:
   ```
   implement-ticket (orchestrator: clarify STOP → options STOP → tests STOP → slice → verify → review STOP)
   ├─ architecture-decision-record  hard-to-reverse decision      (draft, STOP for approval)
   ├─ test-strategy                 every slice                   (test list, STOP for approval)
   ├─ mermaid-diagrams              flow or claim state changed   (propose, STOP for approval)
   └─ review-changes                every finished slice          (report only, never edits)
        └─ suggests → architecture-decision-record / mermaid-diagrams / test-strategy
   ```
   ```
   plan-ticket (docs-only tickets: decisions STOP → draft + consistency check STOP → write STOP)
   ├─ architecture-decision-record
   └─ mermaid-diagrams
   ```
   Each skill can also be used directly. Sub-skills hand control back to their caller.
4. Do not log AI-assisted work in `docs/ai-usage.md`. There is no task log and no logging script. Later pull requests must not add one.
5. Keep changes small and focused, one concern per change. Don't touch unrelated files.
6. Don't add dependencies without stating why; prefer what Spring Boot already manages.
7. When requirements are ambiguous or missing (SLAs, external contracts), stop and ask (see Golden rule); record the answer in `docs/` or an ADR only once the human confirms it.
