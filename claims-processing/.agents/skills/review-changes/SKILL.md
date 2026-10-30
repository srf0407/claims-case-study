---
name: review-changes
description: Read-only review of claims-service changes (a slice, diff, branch or listed files) against the ticket, docs/solution.md, docs/adr and AGENTS.md, producing a findings report and never editing code. Use when asked to review, check or critique changes, a diff, a branch or a slice, and when implement-ticket hands over a finished slice.
---

# review-changes (report only)

You never edit, fix, stage or commit. The report is the whole output.

**Called by:** the developer directly, or `implement-ticket` after a slice.
**Input:** the change set (a `git diff`, branch, or file list) and the ticket. If either is missing
or the scope is unclear, ask before reviewing.
**Returns:** the report below, then control goes back to the caller (or to the developer).

## Check
1. Run `./mvnw verify` (or `mvn verify`); quote the test summary and any ArchUnit failure as printed.
   If the caller already passed a verify result for this exact diff, reuse it. ArchUnit owns the
   layer rules, so don't re-check dependencies by eye. No build yet? Say so.
2. Ticket fit: anything missing, anything extra, anything that contradicts `docs/solution.md` or an ADR.
3. Project conventions from `AGENTS.md`: names (`*UseCase`, `*Service`, `*Port`, `*Client`,
   `*Mapper`), external DTOs kept in their adapter, 4xx/5xx/timeouts turned into domain exceptions
   in the adapter, constructor injection, records.
4. Claims specifics: death-claim priority honoured where the ticket touches it; claim status changes
   only through domain rules; payment callback handles unknown claim IDs and repeats.
5. Tests, against the `test-strategy` rules: the slice's behaviour (incl. negative/edge cases) is
   covered, assertions check outcomes and state, right tool per layer (WireMock for outbound
   adapters), no tests of trivia (getters, records, framework), no sleeps or real clocks.
6. Readability: names, method size, dead code.

## Report
```
Build: <quoted verify summary / "no build yet">

Must fix:   path/File.java:42 - problem - why it matters
Should fix: ...
Nit:        ...

Your call (docs don't say):  <behaviour> - option A / option B
Looked at, not raised:       <item> - reason
Follow-ups: decision → `architecture-decision-record`; stale diagram → `mermaid-diagrams`; weak or missing tests → `test-strategy`
Questions: ...
```
Rank honestly; an empty section is fine. Finish by asking which findings to act on.
