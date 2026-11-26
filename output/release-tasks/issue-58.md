<!-- medos-release-audit: receipts -->
## Outcome and priority

**P1 · Suggested accountable role: Billing frontend/backend + cashier owner**. Release tracker: #219.

Live collection needs a usable accurate receipt; original file-line claims must be checked against current code.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§7 outputs; §9 accounting**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Confirm implemented print/API capability and reuse it where available; define invoice versus payment receipt semantics with the cashier.
2. Render tenant identity, patient/UHID, unique stored document number, dates, line items, discounts, agreed tax fields, total, paid and remaining amounts from authoritative records.
3. Authorize retrieval/reprint by tenant and role and preserve historical financial facts rather than recalculating using current prices.
4. Add print styling for agreed paper sizes/long content; omit application controls and verify actual preview output.
5. Review GST/numbering/content with the hospital accounting owner via #59 and document reprint/void/credit behavior.

## Dependencies and coordination

- #226 — Calculate discounted invoice balances from authoritative receivables
- #59

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Discounted and partial-payment receipts reconcile exactly with stored invoice/payment totals.
- [ ] Later retrieval reproduces the correct historical receipt for the correct tenant/patient.
- [ ] Cashier approves legible print previews including multi-page cases.

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

Billing has nothing printable. No print button, no print stylesheet, no PDF library, no receipt endpoint. A receipt must also carry the tenant name, a unique id, and patient details — the id exists, the rest does not.

### Evidence

- `Billing.jsx` (289 lines) has no `window.print()`, no print button, no receipt component, no download handler. The invoice table's only action is "Pay" (`:188-196`).
- No `@media print` rule anywhere in the stylesheets.
- `frontend/package.json` has no PDF or print dependency: `react`, `react-dom`, `react-router-dom`, `recharts`, `zustand`, `axios`, and two IBM Plex font packages. That is all.
- `BillingController` has six endpoints — create invoice, list patient invoices, unbilled charges, invoice charges, record payment, invoice payments. None returns a rendered document.

**Already in place:**
- `Invoice.invoiceNumber` and `Payment.paymentNumber` are `UNIQUE` in the schema and reach the client today (`InvoiceDTO.java:19`, `PaymentDTO.java:18`).
- `Invoice.gstTotal` and per-charge `gstPercent` / `gstAmount` are available.

**Missing for a compliant receipt:** patient name on the document, tenant name, tenant address, tenant GSTIN, line items, and a CGST/SGST split. `InvoiceDTO` has none of these, and the billing endpoints return raw entities rather than DTOs.

`billingApi.getInvoiceCharges` exists in the client and is **never called** by `Billing.jsx`, so the line items are already fetchable but never displayed.

### Requirements

- A receipt view rendering: tenant name, address and GSTIN; a unique receipt/invoice number; patient name, UHID and age; every line item with quantity, unit price, GST% and amount; subtotal, discount, CGST/SGST or GST total, grand total; payment method, reference and date; and a footer.
- A print stylesheet so the receipt prints cleanly with no navigation, sidebar or buttons.
- A print affordance on the invoice and on a settled payment.
- **Prerequisite:** the tenant name issue — the billing page cannot print a tenant name that the client does not have.
- Wire up `getInvoiceCharges` so line items actually render.
- Decide whether the receipt is also retrievable later (a receipt lookup by number), which matters for disputes and for patients who lose theirs.

### Decisions needed

- **Browser print against a print stylesheet**, or a **server-rendered PDF**. Browser print adds no dependency, works offline, and quality depends on the user's printer — adequate for handing a receipt across a counter. Server PDF needs an endpoint and a library, gives consistent output, and is required the moment receipts are emailed or archived.
- Whether one receipt per payment or per invoice. Both numbers already exist, which makes either possible.
- Whether a legal sequential receipt series is required, and whether it must be gapless.

### Verification

- A settled invoice prints with all fields above populated and no application chrome.
- The printed unique number matches the stored `invoiceNumber` / `paymentNumber`.
- Patient name, UHID, line items and GST all appear, from decrypted data, and nothing PII-encrypted leaks incorrectly.
- Print preview is legible in both A4 and Letter.

### Notes

- Blocked by the tenant-name issue.
- Pairs with the configurable-GST issue: a receipt cannot show a correct tax breakdown while GST rates are hardcoded and per-line tax is absent.
- Context: `docs/feature-releases.md` (item 4).


</details>
