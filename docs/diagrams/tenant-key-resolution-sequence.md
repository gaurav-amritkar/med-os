# Tenant Key Resolution Sequence

## Overview

The diagrams below describe the runtime flow of PII encryption and blind-index
computation through the deepened `tenant_key_resolution_seam`. Each diagram
covers a distinct responsibility extracted into its own module or interface.

## 1. PII field encryption (write path)

The JPA converter never resolves keys itself; it reaches the `TenantKeyResolver` via
the static `TenantKeyResolverFactory`, which Spring populates at startup.

```mermaid
sequenceDiagram
    participant JPA as Hibernate (flush)
    participant Conv as EncryptionUtil<br/>AttributeConverter
    participant Format as PiiCiphertextFormat
    participant Factory as TenantKeyResolverFactory
    participant Holder as TenantKeyHolder<br/>implements TenantKeyResolver
    participant Store as TenantKeyStore (JPA)

    JPA->>Conv: convertToDatabaseColumn(plaintext)
    Conv->>Format: format().encrypt(plaintext)
    Format->>Holder: dekResolver.resolveDek(generation)
    Note over Holder: generation=1, tenant from TenantContext
    Holder->>Store: loadOrCreate(actingTenant)
    alt first use for tenant
        Holder->>Store: insert(tenant, wrap(DEK), null)
        Note over Store: FK to tenants row enforces existence
    end
    Holder-->>Holder: cache.put(tenant, DEK)
    Note over Holder: seed cache BEFORE insert returns<br/>avoids re-entrant recursion during flush
    Format-->>Conv: "kv1.d1:IV||ciphertext||tag"
    Conv-->>JPA: stored ciphertext
```

## 2. Blind index computation (search path)

Search resolves a tenant's blind-index key through the same seam. The index key is
separate from the data key so a KEK re-wrap does not invalidate stored digests.

```mermaid
sequenceDiagram
    participant Controller as PatientSearchController
    participant Util as BlindIndexUtil
    participant Holder as TenantKeyHolder
    participant BiService as BlindIndexKeyService<br/>implements BlindIndexKeyProvider
    participant Store as TenantKeyStore

    Controller->>Util: indexPatientName(name)
    Util->>Holder: resolveDek(null) for TenantContext tenant
    Note over Util: tenant resolved from TenantContext
    Holder->>BiService: findOrNull(tenant)
    alt index key already exists
        BiService->>Store: wrappedBiKeyOf(tenant)
        Store-->>BiService: wrapped key
        BiService-->>BiService: unwrap with KEK<br/>return cached key
    else first use
        BiService->>Store: insertBlindIndexKey(tenant, wrap(new key))
        Note over Store: requires DEK row to exist (FK)
        BiService-->>BiService: cache.put(tenant, key)
    end
    BiService-->>Util: indexKey bytes
    Util-->>Util: HMAC-SHA256(normalised name)
    Util-->>Controller: hex digest (VARCHAR(64))
```

## 3. KEK rotation — online re-wrap

Re-wrapping changes only the wrapper around each key; the DEK and index key bytes are
unchanged, so no ciphertext or digest is rewritten. This is an online operation.

```mermaid
sequenceDiagram
    participant Op as RotationOperation
    participant Holder as TenantKeyHolder<br/>withPreviousKek(oldKEK)
    participant BiService as BlindIndexKeyService
    participant Store as TenantKeyStore

    Op->>Holder: rewrapAll()
    Note over Op: new holder built with new KEK<br/>previous KEK supplied for unwrap

    loop for each tenant with keys
        Holder->>Store: wrappedDekOf(tenant)
        Store-->>Holder: wrapped DEK
        Holder->>Holder: unwrap(wrapped, previousKek)
        Note over Holder: DEK bytes recovered, unchanged

        Holder->>Store: replaceWrappedDek(tenant, wrap(DEK, newKEK))
        Note over Store: same DEK, fresh IV in wrapper

        Holder->>BiService: rewrapAll()
        Note over BiService: same dance for index key

    end

    Note over Holder,BiService: previousKek = null<br/>cache cleared<br/>old material no longer readable with old KEK
```

## 4. Blind index key independent rotation

A targeted rotation of the index key bumps its generation without touching the data key
or any ciphertext. Stored `name_index` digests stop matching by design.

```mermaid
sequenceDiagram
    participant Service as PatientService<br/>or admin tool
    participant BiService as BlindIndexKeyService<br/>implements BlindIndexKeyProvider
    participant Store as TenantKeyStore

    Service->>BiService: rotateIndependently(tenantId)
    BiService->>BiService: new random index key (32 bytes)
    BiService->>Store: biKeyGenerationOf(tenantId)
    Store-->>BiService: current generation
    BiService->>Store: replaceWrappedBiKey(tenantId, wrap(newKey))
    Note over Store: same KEK, new key bytes

    BiService->>Store: advanceBiKeyGeneration(tenantId, +1)
    Store-->>BiService: ack

    BiService-->>BiService: cache.put(tenantId, newKey)
    BiService-->>BiService: log rotation event

    Note over Service: caller must re-index patients<br/>old digests were HMAC with old key
    Note over BiService: DEK and ciphertext untouched
```

## 5. Factory bridge for JPA converter

The static factory mediates between the JPA `AttributeConverter` (which Hibernate
instantiates) and the Spring-managed resolver, so DI is not required in the converter.

```mermaid
sequenceDiagram
    participant Spring as Spring Boot
    participant Config as EncryptionConfig
    participant Factory as TenantKeyResolverFactory
    participant Conv as EncryptionUtil<br/>AttributeConverter
    participant Format as PiiCiphertextFormat

    Spring->>Config: @PostConstruct / init()
    Config->>Factory: setInstance(TenantKeyHolder)
    Note over Config: holder populated with KEK + TenantKeyStore

    ... later, during entity flush ...

    participant Hibernate as Hibernate (flush)
    Hibernate->>Conv: convertToDatabaseColumn(value)
    Conv->>Format: format().encrypt(value)
    Format->>Factory: get()
    Factory-->>Format: TenantKeyResolver
    Format->>Format: dekResolver.resolveDek(generation)
    Format-->>Conv: ciphertext
    Conv-->>Hibernate: stored value

    ... in tests ...
    participant Test as Test
    Test->>Factory: reset() then setInstance(inMemoryResolver)
    Test->>Factory: get() returns test resolver
```
