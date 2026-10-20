## Agent skills

## Maintained build and test target

- Paper 26.3 build 207, channel `STABLE`, API `26.3.build.207-stable` is the
  maintained stable target. Review the live PaperMC
  `https://docs.papermc.io/llms.txt` index and exact-version Javadocs before
  changing it. Keep Minecraft 26.3 and require STABLE releases; never silently
  fall back to 26.2 or an alpha/beta API.
- Build and unit test with JDK 25.0.5 using `JAVA_HOME` and `PATH`; retain
  Java 25 release/source/target. Use Oracle JDK 27.0.1 for the local Paper 26.3 runtime.
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
