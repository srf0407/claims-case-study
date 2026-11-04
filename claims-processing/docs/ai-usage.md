# AI usage

| Task | Outcome | Notes |
|---|---|---|
| Claim intake endpoint | Accepted | POST /claims returns 201 and the new id. Invalid claims are 400 and are not queued. |
| Claim status endpoint | Accepted | GET /claims/{id} returns the stored claim. An unknown id is 404 and a non-UUID id is 400. |
| Payment status callback | Accepted | POST /payments/callback checks the HMAC signature and applies the payment status. Queueing the callback is deferred. |
| Reject incomplete payment callbacks | Accepted | A signed callback missing claimId, outcome, or idempotencyKey, or with an unknown outcome, returns 400 and does not call the use case. |
| Error handling and validation responses | Accepted | Web errors return a ProblemDetail body. An unexpected failure is a generic 500 and is logged. |
| Remove ArchUnit empty-layer allowances | Accepted | withOptionalLayers and failOnEmptyShould are removed. Each layer already has classes, and the dependency rules are unchanged. |
