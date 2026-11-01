# Container Hardening: Non-Root, Resource Limits, and Dropped Capabilities

* **Author:** MedOS team
* **Date:** 2026-10-31

## Overview

Harden all Docker containers in the MedOS stack (backend, frontend, Caddy, Redis, migrate) by running as non-root where possible, setting CPU and memory limits, dropping Linux capabilities, enabling `no-new-privileges`, and using read-only root filesystems with tmpfs for writable paths. The README already notes that no service currently has resource limits, `read_only`, `cap_drop`, or `no-new-privileges`, and that `redis`, `caddy`, and `migrate` still run as root.

## Analysis

**Contrarian / YAGNI** — Does this feature need to exist?
The application runs inside Docker containers on a single host or cluster. If an attacker compromises the backend or frontend, a root container gives them a near-root shell on the host with broad capabilities. Hardening reduces the blast radius of a compromise. The counter-argument is that the containers are behind Caddy TLS and on an internal network, so the attack surface is small. That is true but not sufficient: a vulnerability in the app (deserialization, SSRF, RCE) still lands inside the container, and a root container makes post-exploitation easier. Hardening is low-cost insurance.

**Cost, Quota & Token Economics** — What is the footprint?
Configuration changes only. Resource limits may cause OOM kills or CPU throttling if set too low, which must be tuned. No new services, licenses, or per-request cost.

**UX & Developer Ergonomics** — Is it intuitive?
Transparent to end users. Developers may need to adjust local volume mounts if a container uses a read-only root filesystem and expects to write to paths other than `/tmp` or designated tmpfs mounts. Local `docker compose up` should continue to work without changes unless opted into hardening.

**Migration & Backward Compatibility** — Changing images may require volume ownership changes (non-root user needs write access to mounted volumes). The `migrate` container running Flyway may need a non-root user with write access to migration files. Backend writing to `target/` or logs needs writable tmpfs paths. Rollback is simple: revert the `docker-compose` changes and rebuild old images.

**Observability & SRE Debuggability** — Is it traceable and actionable?
No direct observability impact. Harder-to-debug issues may arise if a non-root user cannot write to expected log paths; logs must go to stdout/stderr (already the case for Docker) or to writable tmpfs.

**Long-Term Tech Debt & Maintenance** — Will it hurt in two years?
Docker Compose changes are declarative and version-controlled. Low maintenance: review when adding new services or changing base images. Capability drops and read-only filesystems occasionally break with application upgrades that need new paths or capabilities.

## Goals vs. Non-Goals

**In-Scope Goals**
- Run backend, frontend, and migrate containers as a non-root user.
- Set CPU and memory limits on all services.
- Add `cap_drop: ALL` and `no-new-privileges: true` to all services.
- Use `read_only: true` root filesystem where the application supports it, with tmpfs mounts for `/tmp`, `/var/run`, and application log/cache paths.
- Keep Caddy and Redis as close to non-root as their official images allow (official Redis runs as root by default; consider a non-root Redis image or a user override).
- Ensure local `docker compose up` still works for developers without manual setup.

**Out-of-Scope Non-Goals**
- Rootless Docker daemon.
- Migration to Kubernetes or PodSecurityPolicy.
- Service mesh (mTLS, sidecars).
- Runtime security tools (Falco, AppArmor profiles beyond Docker defaults).

## Alternatives Weighed

**Option A: Docker Compose hardening (chosen)**
Reason: MedOS already uses Docker Compose in dev and prod. Hardening via Compose is declarative, version-controlled, and requires no new infrastructure.

**Option B: Kubernetes Pod Security Standards**
Reason rejected: MedOS is not on Kubernetes. Adopting K8s solely for container hardening is disproportionate.

**Option C: Do nothing (status quo)**
Reason rejected: Multiple services run as root with no resource limits or capability drops, which is below industry standard for a containerized healthcare application.

## Key Decisions & Staging for Next Steps

1. Start with backend and frontend (application containers) since they are under MedOS control and easiest to convert to non-root.
2. Address Caddy and Redis next; if official images require root, evaluate building hardened variants or using `USER` overrides with volume ownership fixes.
3. Set resource limits conservatively first (256 MB–1 GB memory, 0.25–1 CPU), then tune from production metrics.
4. Add a CI assertion that resolved `docker-compose.prod.yml` has no root users, no missing resource limits, and only Caddy publishes ports (this already exists partially; extend it).
5. Next step: write a `docker-compose.harden.yml` overlay or modify the existing Compose files, test locally, then roll to staging.
