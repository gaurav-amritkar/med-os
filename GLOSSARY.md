# Glossary — MedOS codebase design vocabulary

These terms are used in ADRs, code comments, and architecture reviews across this project. Consistent language is the whole point.

- **module**: anything with an interface and an implementation. Scale-agnostic: function, class, package, or tier-spanning slice. Avoid: component, service, API.
- **interface**: everything a caller must know to use the module correctly: the type signature, invariants, ordering constraints, error modes, required configuration, and performance characteristics. Avoid: API, signature (too narrow).
- **implementation**: what's inside a module, its body of code. Distinct from adapter: a thing can be a small adapter with a large implementation (a Postgres repo) or a large adapter with a small implementation (an in-memory fake).
- **depth**: leverage at the interface. A module is deep when a large amount of behaviour sits behind a small interface, shallow when the interface is nearly as complex as the implementation.
- **seam** (Michael Feathers): a place where you can alter behaviour without editing in that place; the location at which a module's interface lives. Avoid: boundary (overloaded with DDD's bounded context).
- **adapter**: a concrete thing that satisfies an interface at a seam. Describes role (what slot it fills), not substance (what's inside).
- **leverage**: what callers get from depth. More capability per unit of interface they learn. One implementation pays back across N call sites and M tests.
- **locality**: what maintainers get from depth. Change, bugs, knowledge, and verification concentrate in one place rather than spreading across callers. Fix once, fixed everywhere.
- **tenant_isolation_seam**: the module-boundary where per-tenant key material (DEK/KEK) is resolved and isolated. In MedOS, satisfies via `TenantKeyStore` (adapter) behind a small interface of `plan()`, `rotate()`, `wrappedDekOf()`, `findTenantsPendingKekVersion()`.
- **key_rewrap_seam**: the specific seam at which `KeyRewrapService` rewraps tenant key wrappers under a new KEK version, without touching patient data. The deep module here is `TenantKeyStore`; the interface is `Plan` + `Result` records; a real adapter (DB-backed `TenantKeyStore`) replaces any hypothetical pass-through.
- **tenant_key_resolution_seam**: the module boundary where a tenant's current DEK, generation, and blind-index key are resolved from whatever backing store, with caching policy, rotation awareness, and isolation guarantees. In MedOS, satisfied by the `TenantKeyResolver` interface; the production adapter is `TenantKeyHolder`.
- **key_lifecycle_seam**: the seam at which the key lifecycle orchestrates KEK re-wrapping operations, with separate audit recording and execution paths. In MedOS, layered outside `key_rewrap_seam`; `KeyRewrapService` becomes a thin adapter at this seam.