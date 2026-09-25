## Agent skills

## Maintained build and test target

- Paper 26.3 build 41, channel `ALPHA`, API `26.3.build.41-alpha` is the
  deliberately selected experimental target. Review the live PaperMC
  `https://docs.papermc.io/llms.txt` index and exact-version Javadocs before
  changing it. Never silently fall back to 26.2.
- Build and unit test with JDK 25.0.4.1 using `JAVA_HOME` and `PATH`; retain
  Java 25 release/source/target. Use JDK 27 for the local Paper 26.3 runtime.
- Root `pom.xml` owns release metadata. Version `2.5.9.3`, build `229` is
  reserved for this upgrade; reuse it after failures.
- Follow `docs/release-process.md` and `docs/test-server.md`. Preserve
  `servers/Paper-26.2/`; only launch the new `servers/Paper-26.3/` for this upgrade.
- The shared server `/Users/floris/MinecraftServer/test-1mb-3.14-mc-26.3` must
  remain stopped. Only replace the `LootChest` plugin's verified JAR after
  local tests, with a recoverable backup and a deployment lock. Never launch it.

### Issue tracker

Issues and specs are tracked in GitHub Issues for `mrfdev/1MB-LootChest`. See `docs/agents/issue-tracker.md`.

### Domain docs

This repository uses a single-context domain-documentation layout. See `docs/agents/domain.md`.
