/**
 * Idempotency-Key support.
 *
 * The backend requires this header on /pharmacy/dispense, /billing/invoices and
 * /billing/payments, and rejects the request without it. The frontend never sent
 * it, so those three flows could not complete at all.
 *
 * A key identifies one logical operation. Generate a new one per operation, and
 * pass the SAME key when retrying that operation — that is what makes a retry
 * safe. A new key on retry is a second operation, and will post twice.
 */
export function newIdempotencyKey() {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  // crypto.randomUUID is only exposed in a secure context. Fall back rather
  // than sending no key, which the backend rejects outright.
  return `k-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

/**
 * Build an axios config carrying an Idempotency-Key.
 * Pass `idempotencyKey` to reuse a key across retries of the same operation.
 */
export function withIdempotencyKey(config = {}) {
  const { idempotencyKey, ...rest } = config;
  return {
    ...rest,
    headers: {
      ...(rest.headers ?? {}),
      'Idempotency-Key': idempotencyKey ?? newIdempotencyKey(),
    },
  };
}
