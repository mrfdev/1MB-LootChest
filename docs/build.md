# Local build and JDK setup

Run the maintained build from the repository root:

```bash
./mvnw -version
./mvnw clean verify
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

`mvnw` is this project's local launcher for installed Maven, not a downloader.
It requires Python 3.9+, Maven on `PATH`, and installed Oracle JDKs 25 and 27.
Normal Maven dependency and plugin resolution can use the network; Maven and
JDK installations are never downloaded by this workflow. Preserve previous
`target/` and `lootchest/target/` artifacts and reports in `archive/` before
`clean` when they are needed as historical evidence.

| Operation | Required JDK |
| --- | --- |
| Maven itself | Oracle 25 |
| Plugin source compilation | Oracle 25, `--release 25` |
| Test source compilation | Oracle 25, `--release 25` |
| Surefire unit-test forks | Oracle 27 |
| Failsafe integration-test forks | Oracle 27 |
| Child Java processes launched by tests | Oracle 27 through test `JAVA_HOME` and `PATH` |
| Project-local Paper 26.3 smoke runtime | Oracle 27 |

Both plugin and test class files must have class major version 69, which is
Java 25. The package phase runs integration checks, including the release-JAR
policy, before the named distribution artifact is copied. `clean verify` runs
the full Maven test lifecycle. Python tests cover the JDK resolver, launcher
guards and Paper smoke harness; the Paper smoke itself remains a separate
command described in [the local test setup](test-server.md).

## Installed JDK selection

Every invocation resolves the two installed homes independently on macOS:

```bash
/usr/libexec/java_home -F -v 25
/usr/libexec/java_home -F -v 27
```

The launcher checks the selected executable's `java.specification.version`,
`java.version`, `java.vendor` and `java.home`, requires `Oracle Corporation`,
and verifies `javac` has the expected major version. Missing installations,
wrong vendors, wrong majors and invalid homes fail before Maven starts. There
is no fallback to another vendor, version, shell default or downloaded JDK.
The selected homes can change when a newer Oracle patch is installed without
editing project files.

For deliberate selection of another installed Oracle home, or use on another
machine, set absolute paths through the supported overrides:

```bash
ONE_MB_BUILD_JAVA_HOME="$(/usr/libexec/java_home -F -v 25)" \
ONE_MB_TEST_JAVA_HOME="$(/usr/libexec/java_home -F -v 27)" \
  ./mvnw clean verify
```

Overrides receive the same validation as automatic discovery. An ambient
`JAVA_HOME` does not select either JDK. The Paper smoke runner also honors
`ONE_MB_TEST_JAVA_HOME` and validates it before starting the isolated instance.

## Configuration ownership

The launcher sets Maven's `JAVA_HOME` and `PATH` to validated Oracle 25 and
generates a temporary toolchains file containing only the validated Oracle
25 and 27 homes. Compiler, Surefire and Failsafe configurations select their
respective toolchains explicitly. Test forks receive Oracle 27 in their
`JAVA_HOME` and at the front of `PATH`. Temporary configuration is removed
when Maven exits. No JDK discovery/downloader plugin is configured; the only
available toolchain entries point to the two validated installed homes.

To prevent stale machine settings from changing this selection, the launcher
supplies isolated user/global Maven settings and toolchains and sets
`MAVEN_SKIP_RC=1`. Existing files in `~/.m2` and the Maven installation are
left untouched. The normal local Maven dependency cache remains in use, but
user/global mirrors, authentication and profiles are not loaded. This project
currently needs no such custom settings; any future repository configuration
must be integrated deliberately into the maintained launcher.

The launcher rejects nonempty `MAVEN_OPTS`, `MAVEN_ARGS`, `MAVEN_DEBUG_OPTS`,
`MAVEN_BASEDIR`, `MAVEN_CONFIG`, `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`,
`JDK_JAVAC_OPTIONS` and `_JAVA_OPTIONS`. Unset them before
building. It also rejects nonempty project `.mvn/maven.config`,
`.mvn/jvm.config` and `.mvn/extensions.xml`; reconcile any new settings with
the launcher before using them.

Command-line options that replace settings, toolchains, profiles or the POM
are rejected, as are properties that change Java/compiler/test-JVM selection
or skip tests. Failure suppression (`--fail-never`/`-fn`) is also rejected.
For example, `-Dmaven.compiler.testRelease=27`, `-Djvm=...`,
`-DforkCount=0` and `-DskipTests` fail before Maven runs. Use the installed-home
overrides for JDK selection. Ordinary commands such as `./mvnw test` and
`./mvnw -Dtest=MatTest test` retain the same JDK policy. Raw `mvn` lifecycle
builds fail validation with guidance to use `./mvnw`.

## Verification evidence

The launcher's opening lines report both selected homes, versions and vendors;
`./mvnw -version` also reports Maven's actual JVM. Maven reports its selected
compiler and test toolchains during execution. Runtime tests in both Surefire
and Failsafe verify the actual Java 27 process, executable and child Java
selection, while class-file checks verify Java 25 bytecode for production and
test output.

JUnit XML reports in `lootchest/target/surefire-reports/` and
`lootchest/target/failsafe-reports/` record `java.home`, `java.version` and
`java.vendor`. Paper smoke evidence under `target/smoke-paper-26.3/` records
the actual runtime home/version and checks the running server's VM build.
Passing automated checks does not replace the release process's manual
gameplay approval or authorize deployment to the shared server.

The Maven setup follows the official [compiler toolchain configuration](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html#jdkToolchain)
and [Surefire toolchain selection](https://maven.apache.org/surefire/maven-surefire-plugin/examples/toolchains).
The [Paper Java installation guidance](https://docs.papermc.io/misc/java-install/)
was checked through its live documentation index; the project's Oracle vendor
selection and Java 27 test runtime are explicit local requirements.

## Clean verification of 563e4ca on 2026-10-25

The full suite was rerun from clean commit
`563e4ca23c34f9a33dd54039653fe0d96f508fba` on
`codex/oracle-25-build-27-tests`. Every build and test invocation explicitly
unset `LOOTCHEST_ALLOW_DIRTY` with `env -u LOOTCHEST_ALLOW_DIRTY`.
The generated JAR records `source.commit=563e4ca23c34` and
`source.dirty=false`; both Paper smoke profiles reported that same clean source.

These paths record the installations actually used on this Mac, not configuration
pins. Every future invocation discovers and validates the installed homes again.

| Process | Verified executable | Version / vendor |
| --- | --- | --- |
| Maven JVM | `/Library/Java/JavaVirtualMachines/jdk-25.0.5.0.3.jdk/Contents/Home/bin/java` | `25.0.5.0.3`, Oracle Corporation |
| Plugin and test compiler | `/Library/Java/JavaVirtualMachines/jdk-25.0.5.0.3.jdk/Contents/Home/bin/javac` | `javac 25.0.5.0.3`, validated Oracle home above |
| Surefire, Failsafe, child Java and Paper | `/Library/Java/JavaVirtualMachines/jdk-27.0.1.0.3.jdk/Contents/Home/bin/java` | `27.0.1.0.3`, Oracle Corporation |

- `env -u LOOTCHEST_ALLOW_DIRTY ./mvnw clean verify` passed:
  112 unit tests and 14 integration tests, with no failures, errors or skips.
  Both compilation phases reported forked Oracle 25 `javac` with `release 25`.
  Runtime assertions and all JUnit XML reports confirmed Oracle 27 test JVMs.
- All 80 production and 22 test class files have major 69, minor 0 (Java 25,
  without preview bytecode).
- All 24 Python harness tests passed, with no failures or errors, using
  `env -u LOOTCHEST_ALLOW_DIRTY python3 -m unittest discover -s scripts -p 'test_*.py' -v`.
- Both Paper 26.3 build 207 profiles passed: integrated and standalone, each with
  two cold boots, persistence checks, clean shutdown and released ports. Both
  commands used `env -u LOOTCHEST_ALLOW_DIRTY ./scripts/smoke-paper-26.3.py`;
  the standalone profile added `--without-integrations`. Optional plugins were
  restored with their original checksums, the temporary probe was removed, and
  the local instance was confirmed stopped afterward.
- Installed Maven 3.10.0 and compiler plugin 3.14.0 were retained. Surefire is
  explicitly pinned to 3.5.4, matching the existing Failsafe version. No JDK or
  Maven installation was downloaded or changed.

Machine-readable evidence is in
`.scratch/verify-563e4ca-20261025-110831/verification.json`. That directory also
holds the build, Python and smoke console logs, Maven version output, JUnit XML
reports, embedded build metadata, and a copy of the verified JAR and checksum.
Full Paper evidence is retained in its `paper-smoke/20261025-110857/` directory
(integrated) and `paper-smoke/20261025-111031/` directory (standalone), with the
original run output under the matching `target/smoke-paper-26.3/` directories.

The generated artifact is `target/1MB-LootChest-v2.5.9.3-230-CMI-j25-26.3.jar`,
SHA-256 `ae6aad1457388f12e79738dd96a84685fa90bda67a7196f497b5166540a775a6`.
The sidecar checksum is at the same path with `.sha256` appended. This checksum
identifies the artifact built from `563e4ca`, before this documentation update.

The preceding development build and its Paper reports were preserved in
`archive/before-clean-563e4ca-20261025-110831/`. Earlier launcher rejection
checks remain under `.scratch/oracle-toolchains/`; the original launcher and
older build outputs remain in `archive/oracle-toolchains-20261025-105543/`.

The checkout remained clean throughout the verification. Automated checks passed;
manual player gameplay and release approval remain outstanding. The preserved
Paper 26.2 instance and shared server were untouched. No deployment or push was
performed.

## Pending manual gameplay approval for 563e4ca

This checklist applies to the verified JAR from `563e4ca`, using the artifact
and checksum recorded above. All items remain pending. Record the results
against that exact JAR before release approval.

- [ ] **Clients and permissions:** Test Java and Floodgate/Bedrock accounts.
  Preserve usernames and Bedrock prefixes; use actual server UUIDs for permission
  grants. Confirm `/lc info` is public and unprivileged players cannot create,
  edit, reload, give, or respawn chests.
- [ ] **Container lifecycle:** Create, edit, open, empty, break, respawn, and
  reload every supported type: chest, trapped chest, barrel, all 17 shulker
  variants, and all eight copper oxidation/wax states.
- [ ] **Menus and inventory interactions:** Exercise normal clicks, shift-click,
  drag, double-click, hotbar swaps, offhand, dropping, and creative actions.
  Close, quit, reconnect, and reopen after reload. Confirm no stale menu changes
  or duplicated rewards.
- [ ] **Reward delivery:** Test full inventories, overflow, AutoSell interactions,
  and real items carrying third-party metadata, custom components, or nested
  container contents. Confirm intended rewards arrive exactly once with metadata
  intact.
- [ ] **Bolt protection:** Attempt to open and break a locked Lootbox using an
  unauthorized account. Confirm no rewards, container removal, or respawn
  scheduling occurs.
- [ ] **Visuals:** Inspect particles, menu text, hologram placement, names, timers,
  and cleanup through reload/respawn. Check naturally oxidized copper containers
  and CMI's floating `ICON:` versus static `SICON:` display.
- [ ] **Interactive messages and CMI reloads:** Check `/lc list` click-to-edit,
  `/lc audit` click-to-teleport, hover text, colors, and the CMILib clickable-message
  test. Verify `/lc reload` while standing still and hologram recovery using
  `/lc reload` after a CMI hologram reload.
- [ ] **Concurrency and interruptions:** Exercise rapid simultaneous use,
  quit/kick/death/world changes, and shutdown during reload. On a disposable copy
  with backups, verify recovery after forced termination between persistence
  stages and disk-full failures, then inspect saved data and `/lc audit`.

The current fixture is Java-only, so Bedrock checks require a suitable Floodgate
setup. See the [manual approval checklist](releases/paper-26.3-stable.md#remaining-manual-checks)
and [CMI in-game notes](releases/compatibility-2026-10-20.md#in-game-approval)
for the existing test context. Adding this checklist does not record gameplay
approval or change the verified artifact.
