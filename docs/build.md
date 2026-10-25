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

## Local verification on 2026-10-25

These paths record the installations actually used on this Mac, not configuration
pins. Every future invocation discovers and validates the installed homes again.

| Process | Verified executable | Version / vendor |
| --- | --- | --- |
| Maven JVM | `/Library/Java/JavaVirtualMachines/jdk-25.0.5.0.3.jdk/Contents/Home/bin/java` | `25.0.5.0.3`, Oracle Corporation |
| Plugin and test compiler | `/Library/Java/JavaVirtualMachines/jdk-25.0.5.0.3.jdk/Contents/Home/bin/javac` | `javac 25.0.5.0.3`, validated Oracle home above |
| Surefire, Failsafe, child Java and Paper | `/Library/Java/JavaVirtualMachines/jdk-27.0.1.0.3.jdk/Contents/Home/bin/java` | `27.0.1.0.3`, Oracle Corporation |

- `./mvnw -o clean verify` passed using the existing local dependency cache:
  112 unit tests and 14 integration tests, with no failures, errors or skips.
  Both compilation phases reported forked Oracle 25 `javac` with `release 25`.
  Runtime assertions and all JUnit XML reports confirmed Oracle 27 test JVMs.
- All 80 production and 22 test class files have major 69, minor 0 (Java 25,
  without preview bytecode).
- All 24 Python tests passed. Nine actual invalid launcher configurations were
  rejected; an invalid ambient `JAVA_HOME` was ignored safely. Raw Maven lifecycle
  invocation failed with guidance to use `./mvnw`.
- Both Paper 26.3 build 207 profiles passed: integrated and standalone, each with
  two cold boots, persistence checks, clean shutdown and released ports. Optional
  plugins were restored, the temporary probe was removed, and the local instance
  was confirmed stopped afterward.
- Installed Maven 3.10.0 and compiler plugin 3.14.0 were retained. Surefire is
  explicitly pinned to 3.5.4, matching the existing Failsafe version. No JDK or
  Maven installation was downloaded or changed.

Machine-readable evidence is in
`.scratch/oracle-toolchains/verification.json`; the same directory holds
`clean-verify.log`, `python-tests.log`, `negative-checks.log`,
`raw-maven-rejection.log`, and both smoke console logs. Paper's full evidence is
in `target/smoke-paper-26.3/20261025-105630/` (integrated) and
`target/smoke-paper-26.3/20261025-105852/` (standalone).

The generated artifact is `target/1MB-LootChest-v2.5.9.3-230-CMI-j25-26.3.jar`,
SHA-256 `717fbe92d8ed595f72cd38823837fbf331f892d0f5903825bafc48e84c9a117b`.
Previous build outputs and the local launcher's original file were preserved in
`archive/oracle-toolchains-20261025-105543/` before cleaning or editing them.

This was a local development verification with uncommitted source and
`LOOTCHEST_ALLOW_DIRTY=1` for smoke. It is not a clean release approval; manual
player gameplay was not performed. Paper logged warnings from OS detection and
LuckPerms' reflective field mutation, without failing the compatibility checks.
The preserved Paper 26.2 instance and shared server were untouched. This
verification preceded the local commit on `codex/oracle-25-build-27-tests`;
no deployment or push was performed.
