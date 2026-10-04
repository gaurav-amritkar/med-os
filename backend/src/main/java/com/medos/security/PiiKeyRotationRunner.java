package com.medos.security;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.medos.repository.KeyRotationRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * #89: the operator entry point for a KEK re-wrap.
 *
 * <p>The service (#88) knows how to re-wrap; this knows when an operator asked for it.
 * The two concerns are separated because the cost of getting the second one wrong is
 * much higher than the first: a rotation that runs without a human deciding to run it is
 * a security incident, not a chore.
 *
 * <p>So the default is a dry run that writes nothing, {@code --apply} is required to
 * change anything, and {@code --verify} reads every tenant through the same path the
 * application reads through. Nothing here ever removes a KEK version or key material:
 * retirement is a separate, deliberate act, because keeping the old version is what
 * makes a rotation reversible.
 *
 * <p>Procedure and rollback: {@code docs/operations.md} §7d.
 */
@Component
public class PiiKeyRotationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PiiKeyRotationRunner.class);

    private final TenantKeyStore keyStore;
    private final KeyRewrapService rewrap;
    private final byte[] currentKek;
    private final Runnable shutdown;
    private final Map<String, String> configured;

    /**
     * Modes come from {@code medos.key-rotation.*}, i.e. {@code MEDOS_KEY_ROTATION_*}
     * environment variables, not from argv. Two reasons: {@code docker compose run SERVICE
     * --flag} replaces the container command instead of appending to it, so argv flags never
     * reach the JVM; and a key passed on a command line is visible in {@code docker inspect}
     * and in the process list of the host. Both matter more for this tool than convenience.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public PiiKeyRotationRunner(
            TenantKeyStore keyStore,
            KeyRotationRepository rotations,
            org.springframework.context.ConfigurableApplicationContext context,
            @Value("${medos.security.pii-encryption-key:}") String currentKekBase64,
            @Value("${medos.key-rotation.mode:}") String mode,
            @Value("${medos.key-rotation.new-kek:}") String newKek,
            @Value("${medos.key-rotation.target-version:}") String targetVersion,
            @Value("${medos.key-rotation.initiated-by:}") String initiatedBy) {
        this(keyStore, new KeyRewrapService(keyStore, rotations), decode(currentKekBase64),
                context::close, optionsFrom(mode, newKek, targetVersion, initiatedBy));
    }

    PiiKeyRotationRunner(TenantKeyStore keyStore, KeyRewrapService rewrap, byte[] currentKek,
            Runnable shutdown) {
        this(keyStore, rewrap, currentKek, shutdown, Map.of());
    }

    PiiKeyRotationRunner(TenantKeyStore keyStore, KeyRewrapService rewrap, byte[] currentKek,
            Runnable shutdown, Map<String, String> configured) {
        this.keyStore = keyStore;
        this.rewrap = rewrap;
        this.currentKek = currentKek;
        this.shutdown = shutdown;
        this.configured = configured;
    }

    static Map<String, String> optionsFrom(String mode, String newKek, String targetVersion,
            String initiatedBy) {
        Map<String, String> options = new LinkedHashMap<>();
        put(options, "dry-run", mode);
        put(options, "apply", mode);
        put(options, "verify", mode);
        put(options, "new-kek", newKek);
        put(options, "target-version", targetVersion);
        put(options, "initiated-by", initiatedBy);
        return options;
    }

    private static void put(Map<String, String> options, String key, String value) {
        if (value != null && !value.isBlank()) {
            String trimmed = value.trim();
            // A mode is a name, not a key: only the three known ones are accepted.
            if (!key.equals("dry-run") && !key.equals("apply") && !key.equals("verify")) {
                options.put(key, trimmed);
                return;
            }
            String normalised = trimmed.toLowerCase().replace('_', '-');
            if (!normalised.equals("dry-run") && !normalised.equals("apply")
                    && !normalised.equals("verify")) {
                throw new IllegalStateException("medos.key-rotation.mode must be one of "
                        + "dry-run, apply, verify; got: " + value);
            }
            options.put(normalised, "");
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Map<String, String> options = parse(args);

        if (options.containsKey("verify")) {
            // Verifies against the KEK this process booted with, which is the one the
            // application will actually use to read patients.
            verify(currentKek);
            shutdown.run();
            return;
        }
        if (options.containsKey("apply")) {
            apply(options);
            shutdown.run();
            return;
        }
        if (options.containsKey("dry-run")) {
            log.info("{}", report(currentKek, targetVersion(options)));
            shutdown.run();
            return;
        }
        // No mode requested at all: this is an ordinary application boot. Report and carry
        // on serving, so a normal restart can never turn into a rotation.
        log.info("{}", report(currentKek, targetVersion(options)));
    }

    /** What a rotation would do, and which rows would block it. Never writes. */
    String report(byte[] kek, int targetVersion) {
        KeyRewrapService.Plan plan = rewrap.plan(kek, targetVersion);
        StringBuilder out = new StringBuilder();
        out.append("PII KEK rotation dry run (nothing was written). ")
                .append("target kek version: v").append(targetVersion).append(". ")
                .append("tenants with keys: ").append(plan.totalKeyRows()).append(". ")
                .append("tenants to re-wrap: ").append(plan.toRewrap()).append(". ")
                .append("tenants already current: ").append(plan.alreadyCurrent()).append(". ");
        if (plan.safeToProceed()) {
            out.append("All pending rows unwrap with the current KEK; the rotation can proceed.");
        } else {
            out.append("ABORT: these tenants cannot unwrap with the current KEK and must be "
                    + "fixed before rotating: ").append(plan.cannotUnwrap());
        }
        return out.toString();
    }

    private void apply(Map<String, String> options) {
        String newKekBase64 = options.get("new-kek");
        if (newKekBase64 == null || newKekBase64.isBlank()) {
            throw new IllegalStateException(
                    "--apply requires --new-kek=<base64 32-byte KEK>. Refusing to rotate: "
                            + "a rotation with no new KEK would make every tenant unreadable. "
                            + "Generate one with `openssl rand -base64 32`.");
        }
        byte[] newKek = decode(newKekBase64);
        if (newKek.length != 32) {
            throw new IllegalStateException(
                    "--new-kek must decode to exactly 32 bytes, got " + newKek.length);
        }

        int targetVersion = targetVersion(options);
        int inUse = keyStore.maxKekVersionInUse();
        if (targetVersion <= inUse) {
            throw new IllegalStateException("Refusing to rotate: the target version must be ahead "
                    + "of what the fleet is on. Target v" + targetVersion + ", in use v" + inUse
                    + ". Rotating to a version already in use would rewrite wrappers for no reason.");
        }

        KeyRewrapService.Plan plan = rewrap.plan(currentKek, targetVersion);
        if (!plan.safeToProceed()) {
            throw new IllegalStateException("Refusing to rotate: will abort, because these tenants "
                    + "cannot unwrap with the current KEK: " + plan.cannotUnwrap());
        }
        KeyRewrapService.Result result = rewrap.rotate(currentKek, targetVersion, newKek,
                options.getOrDefault("initiated-by", "unknown"));

        if (result.status() == KeyRewrapService.Status.ABORTED) {
            throw new IllegalStateException("KEK rotation will abort: " + result.detail());
        }

        // The rotation is not trusted until it has been read back. This checks against the
        // *new* KEK, because the application is still running with the old one; the
        // separate --verify run is what proves the restarted application can read.
        verify(newKek);
        log.warn("KEK rotation to v{} complete. Keep the previous KEK version until --verify has "
                + "passed on a restarted application; retirement is a separate, deliberate step.",
                targetVersion);
    }

    /**
     * Every tenant must unwrap with {@code kek}, or say so loudly. Wrapped material is read
     * the same way the application reads it, so this fails for exactly the rows that would
     * fail in production.
     */
    private void verify(byte[] kek) {
        List<UUID> unreadable = new ArrayList<>();
        List<UUID> checked = new ArrayList<>();
        for (UUID tenantId : keyStore.tenantIdsWithKeys()) {
            byte[] wrapped = keyStore.wrappedDekOf(tenantId).orElse(null);
            byte[] dek;
            try {
                // Direct unwrap under the KEK under test. Going through the running
                // resolver would only ever prove the KEK the app booted with.
                dek = wrapped == null ? null : KeyWrapCipher.unwrap(wrapped, kek);
            } catch (RuntimeException e) {
                unreadable.add(tenantId);
                continue;
            }
            if (dek == null || dek.length == 0) {
                unreadable.add(tenantId);
                continue;
            }
            checked.add(tenantId);
        }
        if (!unreadable.isEmpty()) {
            throw new IllegalStateException("Verification FAILED: " + unreadable.size()
                    + " tenant(s) cannot unwrap their DEK with the KEK this application is running "
                    + "with: " + unreadable
                    + ". Do not retire the previous KEK version. If the application was restarted "
                    + "with the new KEK before the re-wrap finished, re-run --apply.");
        }
        log.info("PII KEK verification passed for {} tenant(s).", checked.size());
    }

    private int targetVersion(Map<String, String> options) {
        String raw = options.get("target-version");
        int version = 2;
        if (raw != null) {
            try {
                version = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("--target-version must be an integer, got: " + raw);
            }
        }
        if (version < 1) {
            throw new IllegalStateException("--target-version must be 1 or greater, got: " + version);
        }
        return version;
    }

    /**
     * Strict flag parsing: an unrecognised option is a mistake worth surfacing, not
     * something to ignore while a rotation runs around it.
     */
    private Map<String, String> parse(ApplicationArguments args) {
        List<String> known = List.of("dry-run", "apply", "verify", "new-kek", "target-version",
                "initiated-by");
        Map<String, String> options = new LinkedHashMap<>(configured);
        for (String arg : args.getNonOptionArgs()) {
            throw new IllegalStateException("Unexpected argument: " + arg);
        }
        for (String name : args.getOptionNames()) {
            String key = name.startsWith("--") ? name.substring(2) : name;
            if (!known.contains(key)) {
                throw new IllegalStateException("Unknown rotation option: --" + key
                        + ". Known options: --dry-run, --apply, --verify, --new-kek, "
                        + "--target-version, --initiated-by. Refusing to continue, because a "
                        + "misspelled flag must not turn into an unintended action.");
            }
            options.put(key, args.getOptionValues(name).isEmpty() ? "" : args.getOptionValues(name).get(0));
        }

        List<String> modes = new ArrayList<>();
        for (String mode : List.of("dry-run", "apply", "verify")) {
            if (options.containsKey(mode)) {
                modes.add(mode);
            }
        }
        if (modes.size() > 1) {
            throw new IllegalStateException("Choose one mode only, got: " + modes);
        }
        return options;
    }

    private static byte[] decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(
                    "No PII encryption key is configured (medos.security.pii-encryption-key). "
                            + "Refusing to touch key material without one.");
        }
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The configured PII encryption key is not valid base64", e);
        }
    }
}