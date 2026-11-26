<!-- medos-release-audit: aiclaims -->
## Outcome and priority

**P1 · Suggested accountable role: Product + clinical + frontend/backend lead**. Release tracker: #219.

Current suggestions use keyword matching and heuristic scores, not calibrated clinical confidence. The original issue also suggests logging patient text; that must not be implemented.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§8**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Rename existing UI/help/API presentation to catalogue/keyword suggestions and explain the matching reason without implying clinical treatment confidence.
2. Remove or clearly label heuristic score presentation and misleading LLM/clinical-intelligence marketing claims.
3. Make feature configuration behavior intentional and tested; remove dead provider config or reserve it behind a disabled, documented future feature.
4. Keep prescription selection editable and clinician-reviewed; no automatic dose/frequency/duration decisions.
5. Treat model-backed documentation as a separate post-pilot design/evaluation task linked from the tracker; do not block a non-AI pilot on an LLM.
6. Audit provenance through model/version/record references and decisions only; explicitly supersede the historical instruction to copy patient text into ordinary audit logs.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Current screens accurately identify keyword/catalogue suggestions and do not claim calibrated confidence.
- [ ] Disabled functionality behaves as documented and no provider call occurs unexpectedly.
- [ ] A suggestion cannot silently become an unreviewed prescription.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.

## Historical issue context

The execution plan above supersedes conflicting historical implementation/status assumptions below. This text is retained for traceability; it is not confirmation of current behavior. In particular, do not implement old migration numbers, plaintext clinical audit logging or obsolete missing-feature claims.

<details>
<summary>Previous issue description</summary>

### Summary

The BRD sells an LLM-powered medicine advisor as one of three product differentiators and as a paid add-on tier. The code does local keyword matching. There is no LLM client on the classpath, no outbound call, and three pieces of configuration that do nothing.

**Decision needed before building: build the LLM path, or stop advertising it.** Shipping as-is is the worst of the three options — marketing promises Claude, the code returns substring matches.

### Evidence (`main` @ `42344e9`)

- `AiMedicineService.java` is 100 lines with no HTTP client and no prompt construction. It does `text.contains(disease_keyword)` plus catalog token matching.
- `backend/pom.xml` has **no** Anthropic, Claude, OpenAI or LangChain dependency.
- `application.yml:59-61` configures `anthropic-api-key` and `model: claude-3-5-sonnet-20241022`. **Nothing reads either property.**
- `AiMedicineService.java:26-27` declares `@Value("${medos.ai.enabled:false}") private boolean aiEnabled` and the field is **never read** — the flag cannot disable anything.
- `ANTHROPIC_API_KEY` is not in `.env` or `docker-compose.yml`, so no key is even supplied in any environment.
- `encounters.ai_note` has **no writer anywhere** (`grep setAiNote` → 0 hits), so PRD §1's `MUTABLE_UNTIL_SIGNED` rule is also unimplemented.

### What the spec requires

- PRD §2: the endpoint should "Trigger Anthropic API. Match against diseaseMedicineMap. Join matching hits with medicine_catalog."
- BRD §2: "integrated LLM-based clinical decision support (Claude AI)".
- BRD §4: an "Intelligence Layer … Anthropic Claude AI integration over external HTTP requests".
- BRD §12: an AI add-on tier with "per-query cost pass-through".

### Requirements if the LLM path is chosen

1. Add the client dependency and a `MedosLlmClient` behind the existing `medos.ai.enabled` flag, so the flag finally means something. Disabled must degrade to today's keyword matcher, not to an error.
2. **Implement PII sanitisation before any outbound call.** BRD §14 Assumption 4 requires patient identifiers to be sanitised locally before being pushed to an external LLM, and BRD §11 requires results be hard-bounded to the local catalog. Today neither is a real constraint because nothing leaves the instance — the moment this is built, both become live. Send only the clinician's free-text description, never patient name, UHID, phone, email or identifiers.
3. Enforce the catalog bound on the response: discard any suggestion not present and active in `medicine_catalog`, so a hallucinated drug can never be offered.
4. Write `encounters.ai_note`, and implement `MUTABLE_UNTIL_SIGNED` — reject writes once the encounter is signed.
5. Record a suggestion event (medicine ids offered, which were accepted) so BRD §9's "AI suggestion acceptance rate" KPI becomes measurable and §12's per-query metering has something to count.
6. Log token usage and latency per call.
7. Surface AI state honestly in the UI: when the flag is off, say the advisor is running in offline mode rather than implying a model answered.
8. Record any patient text sent upstream in the audit trail, as an explicit disclosure.

### Requirements if the LLM path is rejected

Delete the dead `anthropic-api-key` / `model` config and the unread `aiEnabled` field, rename the feature from "AI Medicine Advisor" to what it is (a catalogue keyword matcher), and correct the BRD and the marketing copy. The BRD's own bounding requirement — only return medicines in the local catalog — is already satisfied and worth keeping in the description.

### Verification

- With `medos.ai.enabled=false`, suggestions still work and the response says offline mode.
- With the flag on, the outbound payload is asserted in a test to contain no patient name, UHID, phone or email.
- A model response naming a drug absent from `medicine_catalog` is filtered out, asserted with a fixture.
- `ai_note` is written on suggestion and rejects modification after the encounter is signed.
- Suggestion and acceptance events are queryable for the acceptance-rate KPI.

### Notes

- Tracked from `docs/requirements-traceability.md` §1.1. Needs a product decision before any code is written.
- Interacts with #59 (configurable GST) only if AI-suggested medicines are ever priced automatically — they currently are not.


</details>
