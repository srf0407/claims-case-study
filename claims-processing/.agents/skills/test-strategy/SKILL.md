---
name: test-strategy
description: Decides what is worth testing in a claims-service slice and how - per layer (domain, application, adapter/in/web, adapter/out with WireMock, persistence, end-to-end, ArchUnit) - and proposes a short test list for approval before tests are written. Use for every implement-ticket slice, when asked what or how to test, when writing or reviewing tests, or when review-changes flags weak or missing tests.
---

# test-strategy

**Called by:** `implement-ticket` on every slice (after the options gate), the developer directly,
or as a follow-up suggested by `review-changes`.
**Input:** the slice and the chosen option. **Returns:** a test list, STOP; after approval, the
tests are written per the rules below, then control goes back to the caller.
If the expected result of a case isn't defined in the ticket or docs, ask; never invent it.

## 1. Propose - STOP
List only the tests worth writing for this slice:

| Layer | Test name | Why it matters |
|---|---|---|
| domain | `should_reject_transition_from_paid_to_submitted` | invalid status change |

Keep it to the behaviour this slice adds or changes. Then STOP for approval.

## 2. What and how, per layer
- **domain** - plain JUnit, no Spring, no mocks: claim status transitions (allowed and refused),
  death-claim priority/SLA rules, value-object validation (blank IDs, negative amounts).
- **application** (use cases) - plain JUnit with hand-written fakes of the `port/out` interfaces
  (Mockito only when a fake is clumsy): client unknown → claim rejected, policy refuses → rejected,
  all checks pass → payment initiated, same payment status twice → no second state change.
- **adapter/in/web** - `@WebMvcTest` + MockMvc, use case mocked: bean validation and missing
  fields → 400, error response body shape, status codes, DTO ↔ command mapping, payment callback
  with unknown claim ID and duplicate callback. No business logic asserted here.
- **adapter/out/{clientregistry,policymanager,payment}** - WireMock through the port: request
  sent (method, path, body), happy path, 404, other 4xx, 5xx, timeout → each a domain exception.
  Adding `org.wiremock:wiremock-standalone` (test scope) is a new dependency: ask first.
  ```java
  @RegisterExtension
  static WireMockExtension policyManager = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort()).build();
  // client = new PolicyManagerClient(RestClient.builder().baseUrl(policyManager.baseUrl())
  //     .requestFactory(factoryWithReadTimeout(300ms)).build(), new PolicyManagerMapper());
  policyManager.stubFor(get("/policies/P-77").willReturn(serverError()));
  assertThatThrownBy(() -> port.checkPolicy(new PolicyNumber("P-77")))
      .isInstanceOf(ExternalSystemUnavailableException.class);
  ```
- **adapter/out/persistence** - small contract test of the in-memory repository: save, find by
  ID, not found, update status, ordering if death claims are fetched first.
- **end-to-end** - one or two `@SpringBootTest` happy paths with WireMock for all three systems:
  submit claim → paid via callback, and the same for a death claim.
- **architecture** - `ArchitectureTest` (ArchUnit) covers layering; don't duplicate it.

## 3. Rules for every test
- Test observable behaviour, not private methods or call order; one reason to fail per test.
- Name says the behaviour: `should_reject_claim_when_client_not_found` or a `@DisplayName` sentence.
- Body in given / when / then blocks.
- Assert the outcome and state (status, exception type, saved claim), not just "no exception".
- Mock only at port boundaries; never mock domain objects or the class under test.
- Deterministic: inject a `Clock` for SLA deadlines; no `Thread.sleep`, no real network, no random.
- Build `Claim` via a test data builder (`aClaim().death().submittedAt(t0).build()`), not long constructors.
- Cover negatives and edges: SLA boundary, duplicate callbacks, missing fields, unknown IDs.
- Skip: getters/setters, records, Spring wiring, framework behaviour. Coverage % is not a goal.
