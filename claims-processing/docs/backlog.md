# Backlog

Tick items off as they're done. Titles only; details are clarified per ticket with `implement-ticket`.
Draft order; reorder or cut freely.

## 0. AI workspace setup
- [x] Research AI context files and skills for the workflow
- [x] Write AGENTS.md (golden rule, layer map, conventions)
- [x] Set up project skills (implement-ticket, test-strategy, architecture-decision-record, mermaid-diagrams, review-changes)
- [x] Create this backlog

## 1. Understand and decide

- [x] Confirm scope and assumptions from the case study
- [x] Context diagram of all systems
- [x] ADR: single service with outbound ports
- [x] ADR: sync vs async integration (payment callback)
- [x] ADR: death-claim prioritisation approach
- [x] Claim status lifecycle (state diagram)

## 2. Project setup

- [x] Spring Boot + Maven build and wrapper
- [x] ArchUnit architecture test
- [x] WireMock test setup
- [x] CI: verify on PRs and main
- [x] Dockerfile + docker-compose

## 3. Domain and use cases

- [x] Claim domain model (types, statuses, transition rules)
- [ ] Submit claim use case
- [ ] Validate client use case
- [ ] Assess policy and benefits use case
- [ ] Initiate payment use case
- [ ] Apply payment status use case
- [ ] Death-claim priority handling
- [ ] Remove ArchUnit empty-layer allowances (withOptionalLayers, failOnEmptyShould) once each layer has classes



## 4. Adapters

- [ ] Claim intake endpoint (`POST /claims`)
- [ ] Claim status endpoint (`GET /claims/{id}`)
- [ ] Payment status callback endpoint
- [ ] Client Registry client + WireMock tests
- [ ] Policy Manager client + WireMock tests
- [ ] Payment client + WireMock tests
- [ ] In-memory claim repository
- [ ] Error handling and validation responses



## 5. Documentation

- [ ] Solution description
- [ ] Assumptions
- [ ] Recommended changes to existing systems
- [ ] System interactions (sequence diagrams)
- [ ] AI usage write-up
- [ ] README: how to run and test



## 6. Optional

- [ ] LocalStack integration (SQS / DynamoDB)
- [ ] Final check against the brief