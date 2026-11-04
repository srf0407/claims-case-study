# AI usage

| Task | Outcome | Notes |
|---|---|---|
| Claim intake endpoint | Accepted | POST /claims returns 201 and the new id. Invalid claims are 400 and are not queued. |
| Claim status endpoint | Accepted | GET /claims/{id} returns the stored claim. An unknown id is 404 and a non-UUID id is 400. |
| Payment status callback | Accepted | POST /payments/callback checks the HMAC signature and applies the payment status. Queueing the callback is deferred. |
| Reject incomplete payment callbacks | Accepted | A signed callback missing claimId, outcome, or idempotencyKey, or with an unknown outcome, returns 400 and does not call the use case. |
| Error handling and validation responses | Accepted | Web errors return a ProblemDetail body. An unexpected failure is a generic 500 and is logged. |
| Remove ArchUnit empty-layer allowances | Accepted | withOptionalLayers and failOnEmptyShould are removed. Each layer already has classes, and the dependency rules are unchanged. |
| Death-claim SLA warning and breach alarms | Accepted | Scheduled scan emits one warning and one breach through a logging port. CloudWatch stays the production option. |
| Claim.restore for a stored claim | Accepted | Restores the id, fields, status, reason, and events. Rejects a null id, a null status, a null or empty event list, and the same field and rejection-reason rules as accept. |
| LocalStack SQS and DynamoDB adapters | Accepted | Profile localstack switches on the SQS queue and DynamoDB repository. In-memory stays the default. A poison message, including an unknown claim type, is logged at WARN with the message id and is not deleted. |
| DynamoDB open death claims | Accepted | findOpenDeathClaims scans for DEATH claims in ACCEPTED or AWAITING_ANALYST and follows the page token. |
