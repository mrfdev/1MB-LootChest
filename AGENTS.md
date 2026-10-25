## Agent skills

## Maintained build and test target

- Paper 26.3 build 207, channel `STABLE`, API `26.3.build.207-stable` is the
  maintained stable target. Review the live PaperMC
  `https://docs.papermc.io/llms.txt` index and exact-version Javadocs before
  changing it. Keep Minecraft 26.3 and require STABLE releases; never silently
  fall back to 26.2 or an alpha/beta API.
- Use `./mvnw clean verify` for the canonical local build. Maven and compilation
  of plugin and test sources use installed Oracle JDK 25 with Java 25
  release/source/target. Unit and integration tests, including forked JVMs, and
  the local Paper 26.3 runtime use installed Oracle JDK 27.
- Resolve JDKs on each invocation with `/usr/libexec/java_home -F -v 25` and
  `/usr/libexec/java_home -F -v 27`; validate the actual major version, Oracle
  vendor and compiler. Never pin a patch-version installation directory or
  download a JDK automatically. Explicit installed-home overrides are
  `ONE_MB_BUILD_JAVA_HOME` and `ONE_MB_TEST_JAVA_HOME`; both undergo validation.
- The local launcher requires installed Maven and Python 3.9+. It supplies
  isolated Maven settings and toolchains, bypasses Maven RC files, and rejects
  conflicting CLI properties, injected JVM options and stale `.mvn` settings.
  Use the launcher rather than raw `mvn`; do not bypass its checks. See
  `docs/build.md` for supported commands, overrides and diagnostics.
- Run `python3 -m unittest discover -s scripts -p 'test_*.py' -v` for launcher
  and smoke-harness checks. Verify actual compiler/test JVM paths, versions and
  vendors, plus Java 25 class major 69 for plugin and test classes. Preserve
  previous artifacts/reports before cleaning when needed as historical evidence.
- Root `pom.xml` owns release metadata. Version `2.5.9.3`, build `230` is
  reserved for the stable migration; reuse it after failures.
- Follow `docs/release-process.md` and `docs/test-server.md`. Preserve
  `servers/Paper-26.2/`; only launch isolated instances under
  `servers/Paper-26.3/` for this migration.
- The shared server `/Users/floris/MinecraftServer/test-1mb-3.14-mc-26.3` must
  remain stopped and untouched during compatibility work. Deployment is a
  separate request and requires a verified JAR, recoverable backup and lock.

### Issue tracker

Issues and specs are tracked in GitHub Issues for `mrfdev/1MB-LootChest`. See `docs/agents/issue-tracker.md`.

### Domain docs

This repository uses a single-context domain-documentation layout. See `docs/agents/domain.md`.
