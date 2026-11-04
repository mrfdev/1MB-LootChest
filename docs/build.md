# Local build and JDK setup

The current candidate is build 231 on Paper 26.3 STABLE build 241. See the
[AutoSell verification record](releases/autosell-2026-11-04.md) for the observed
Oracle compiler/test paths, bytecode target, integrated and standalone test
workflow, and remaining real-client gameplay checks. The older results below
are retained as historical evidence. In particular, `563e4ca` reused build 230
after the [approved build-230 artifact](releases/compatibility-2026-10-20.md#approval-record)
was tagged at `3dc612feb28e`; approval applies only to that tag's source and
checksum. The pending `563e4ca` checklist below does not revoke that approval or
serve as the current build-231 results table.

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
against that exact JAR before release approval. Use the
[step-by-step plan below](#step-by-step-manual-gameplay-test-plan-for-563e4ca)
to execute these checks in a later test session, then record outcomes in the
[manual gameplay results table](#manual-gameplay-results-for-563e4ca).

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

## Step-by-step manual gameplay test plan for 563e4ca

This plan is for later execution. Every gameplay case remains pending; adding
these instructions does not run tests, start a server, deploy an artifact, or
record release approval. Use the existing verified
`target/1MB-LootChest-v2.5.9.3-230-CMI-j25-26.3.jar` from `563e4ca`, with SHA-256
`ae6aad1457388f12e79738dd96a84685fa90bda67a7196f497b5166540a775a6`.
Do not rebuild it for this plan.

The intended fixture is the isolated Paper 26.3 build 207 server under
`servers/Paper-26.3/`, running on installed Oracle Java 27. Server preparation
and startup require a separate authorized session; this plan supplies no launch
or deployment commands. Leave `servers/Paper-26.2/` and the shared server at
`/Users/floris/MinecraftServer/test-1mb-3.14-mc-26.3` untouched.

Commands with `/` are in-game commands unless stated otherwise. Console commands
omit `/`. Replace `<PLAYER>` with the exact online username, preserving any
Floodgate prefix, and `<BOX>` with the recorded test Lootbox name. Replace
`<ADMIN_UUID>` only with the administrator's authoritative server UUID from
trusted local records. Do not use a nickname, generate a UUID from a name, or
query an external identity service. See the [command reference](commands.md).

### 1. Confirm the candidate and record the baseline

Once the isolated fixture is separately available, connect with a Java 26.3
client to `127.0.0.1:32931` as the test administrator and run:

```text
/lc info
/lc audit
/lc list
/lc tp newtest1
```

Expected: version `2.5.9.3-230`, build `230`, source `563e4ca23c34` without a
dirty marker, Paper 26.3 build 207 `STABLE`, and API `26.3.build.207-stable`.
The Java 25 value in plugin build information describes the bytecode target;
the fixture runtime must be Oracle Java 27. The recorded integration versions
are CMI `9.8.11.0` and CMILib `1.6.1.0`.

Save the initial audit and list output. Use a clear test area near the existing
fixtures and create new names beginning with `qa563_`. Preserve the original
fixtures, including `newtest1`.

### 2. Establish administrator and ordinary-player roles

Use a non-OP ordinary player with no LootChest administration permissions or
Bolt bypass. If a fresh test administrator needs temporary access, use this
console command with their actual server UUID:

```text
lp user <ADMIN_UUID> permission settemp lootchest.admin true 1h deny
```

Expected: the temporary permission applies to that UUID. If the temporary grant
already exists, inspect it instead of replacing it. Record whether this session
created the grant so cleanup removes only that grant. The command follows the
[LuckPerms permission command syntax](https://github.com/LuckPerms/wiki/blob/master/pages/Permission-Commands.md).

Repeat the player-facing cases with a Floodgate/Bedrock account when a suitable
fixture is available. The current fixture has no Geyser/Floodgate setup, so mark
Bedrock coverage **BLOCKED**, not passed, until that setup is separately provided.

### 3. Create a deterministic test Lootbox

Place a single chest containing eight diamonds and one renamed, enchanted tool.
If Bolt automatically locks it, run `/bolt unlock` and select the chest before
ordinary-player tests. While looking at the filled chest, run:

```text
/lc create qa563_chest
```

In the editor, verify the saved contents and set every occupied reward slot to
100% chance. Close the editor, then run:

```text
/lc randomspawn qa563_chest 0
/lc maxfilledslots qa563_chest 0
/lc setprotection qa563_chest 0
/lc settime qa563_chest 10
/lc setholo qa563_chest <gold>QA 563 Chest
/lc respawn qa563_chest
/lc getname
/lc audit qa563_chest
```

Expected: creation opens the editor, the targeted block is `qa563_chest`, the
reward template matches the recorded contents, and the audit shows no new
problem. The box stays at its chosen location. `settime` takes minutes and
immediately respawns the box; each deliberate respawn creates a new reward roll.

### 4. Verify command permissions

As the ordinary player, run:

```text
/lc info
/lc create qa563_denied
/lc edit qa563_chest
/lc give qa563_chest <PLAYER>
/lc respawn qa563_chest
/lc reload
```

Expected: `/lc info` succeeds. The administration commands are denied without
creating a definition, opening an editor, delivering rewards, respawning the
box, or reloading the plugin. The ordinary player can open the unlocked box
when its LootChest protection period is disabled.

### 5. Exercise collection, breaking, and timed respawn

As the ordinary player, take only part of the contents and close the inventory.
With the recorded fixture configuration, the box remains with the leftovers.
Empty it and close again: the physical container and its effects disappear.
The fixture enables natural destruction, so count any configured empty-container
item drop separately from reward items.

As the administrator, run:

```text
/lc respawn qa563_chest
```

Break the box in survival mode and count the drops. Expected: remaining rewards
drop once, the physical box disappears, and its saved definition remains.
Then run:

```text
/lc settime qa563_chest 1
```

Empty and close the newly spawned box promptly. Wait without forcing another
spawn. Expected: one replacement appears about one minute after the forced
spawn, with the saved reward template and effects. Finish with:

```text
/lc audit qa563_chest
/lc settime qa563_chest 10
```

### 6. Repeat the lifecycle for every supported container

Create separately named `qa563_` fixtures and repeat create, edit, open, collect,
break, respawn, and reload for all 28 supported variants:

- Chest, trapped chest, and barrel.
- Uncolored shulker box and all 16 colored shulker boxes.
- Base, exposed, weathered, and oxidized copper chests, each waxed and unwaxed.

Use `/lc edit <BOX>` to exercise the type-selection menu as well. Expected:
every variant registers with the correct type and completes the lifecycle
without losing its template or duplicating rewards.

Change oxidation or wax state on an already registered copper fixture, including
a naturally oxidized case, then run:

```text
/lc reload
/lc audit <BOX>
```

Wait for reload completion before auditing. Expected: the live container remains
recognized, with no type mismatch or missing effects.

### 7. Exercise menus and stale sessions

Run:

```text
/lc edit qa563_chest
```

Visit the contents, chances, type, time, particle, and copy menus. Restrict copy
destinations to the new `qa563_` fixtures. Exercise normal clicks, shift-click,
drag, double-click, hotbar swaps, offhand swaps, dropping, and creative actions.

Expected: control icons cannot be extracted or displaced. The contents editor
intentionally permits item movement, and intended edits survive closing and
reopening it. Repeat with close, quit, reconnect, and reopen.

With an editor open, have a second operator run this console command:

```text
lc reload
```

After reload completes, open a fresh editor and save a distinct change. Expected:
an older session cannot overwrite that change, reopen unexpectedly, or modify
another Lootbox.

### 8. Verify overflow, metadata, and template isolation

Create `qa563_meta` using real items carrying third-party metadata, custom
components, and a filled shulker item. Use a chest or barrel for the nested-item
case. Record the template and make reward chances deterministic as in step 3.

With an empty recipient inventory, run once:

```text
/lc give qa563_meta <PLAYER>
```

Repeat once with the recipient's inventory full. Expected: each command produces
exactly one configured reward roll. Overflow drops at the recipient, existing
inventory contents remain intact, and inventory plus ground totals match the
intended rewards.

Modify a delivered item, respawn the fixture, and inspect a fresh reward:

```text
/lc respawn qa563_meta
```

Expected: names, enchantments, damage, custom data/components, and nested contents
are preserved; modifying a delivered copy does not alter the saved template.
Repeat with AutoSell enabled and disabled in a suitable integration fixture.
AutoSell is absent from the current fixture, so record that coverage as
**BLOCKED** until it is separately available.

### 9. Verify temporary protection and Bolt protection

As the administrator, run:

```text
/lc setprotection qa563_chest 10
/lc respawn qa563_chest
```

Immediately try opening and breaking the box as the ordinary player. Expected:
access is denied without rewards or container removal during the protection
window, then becomes available after expiry. Reset protection:

```text
/lc setprotection qa563_chest 0
```

As the owner, run `/bolt lock private` and select the chest. Have an untrusted,
non-OP player attempt to open and break it. Expected: Bolt denies both actions;
contents, container, and lifecycle state stay unchanged, and no respawn is
scheduled by the denied action. Compare `/lc audit qa563_chest` before and after.
Use `/bolt unlock` and select the chest afterward. See the
[Bolt command reference](https://github.com/pop4959/Bolt/wiki/#commands).

### 10. Inspect visuals, interactive messages, and CMI recovery

Run:

```text
/lc list
/lc audit qa563_chest
/lc setholo qa563_chest <gold>QA 563 Updated
/lc respawn qa563_chest
```

Expected: clicking a list entry opens its editor; clicking the chest name in
targeted audit output teleports to it. Hover text and colors render correctly.
Exactly one updated hologram appears at the intended location. Select different
particle effects through the editor and inspect their placement and cleanup.

Run the CMILib clickable-message check:

```text
/cmi ctellraw <PLAYER> <T>&aCMILib click test</T><H>&eRuns /lc info</H><C>/lc info</C>
```

Expected: the recipient sees the hover hint, and clicking runs `/lc info`.
Compare the existing floating `ICON:` and static `SICON:` displays near
`spawn:-15,73,-55`. Then run:

```text
/cmi hologram reload
/lc reload
```

Expected: LootChest reload restores transient holograms discarded by the CMI
reload, once each, without orphaned displays. Repeat `/lc reload` while standing
still to check client refresh. See the
[CMI in-game notes](releases/compatibility-2026-10-20.md#in-game-approval).

The current fixture has `Timer_on_hologram.Show_Timer_On_Hologram` set to false.
During the later session, temporarily enable it in the isolated configuration,
reload, confirm the displayed countdown advances, then restore the original
setting and reload again.

### 11. Test concurrency and interruptions

Respawn a counted fixture and have two ordinary players loot it simultaneously,
including rapid clicks and competing open/break actions. Expected: total rewards
across both players, the chest, and ground equal one spawn's contents. Count
intentional additional respawns and `/lc give` calls as separate reward rolls.

Repeat with disconnects, kicks, death, and world changes. For the kick case, use
this console command:

```text
kick <PLAYER> QA reconnect check
```

After reconnecting or returning, run:

```text
/lc audit <BOX>
```

Expected: no stale access, duplicate effects, duplicate rewards, or broken
lifecycle state.

On a separately prepared disposable copy with recoverable backups, exercise
shutdown during reload using console commands:

```text
lc reload
stop
```

After separately controlled recovery, inspect `/lc info`, `/lc audit`, saved
definitions, and reward templates. Forced termination between persistence stages
and disk-full failures require a disposable, size-limited environment. Keep
those cases **BLOCKED** until that environment and recovery procedure are
prepared; an ordinary clean shutdown does not satisfy them.

### 12. Clean up and record the outcome

Remove the test definitions:

```text
/lc remove qa563_chest
/lc remove qa563_meta
```

Repeat for every other recorded `qa563_` fixture. Remove test Bolt locks and
restore configuration changes. Only if step 2 created a temporary grant for this
session and it is still present, remove it using the console:

```text
lp user <ADMIN_UUID> permission unsettemp lootchest.admin
```

Run:

```text
/lc list
/lc audit
```

Expected: the test definitions and displays are gone, original fixtures remain,
and no new audit findings remain compared with the baseline.

Record **PASS**, **FAIL**, or **BLOCKED** for each case in the
[results table](#manual-gameplay-results-for-563e4ca), with the client type,
exact JAR checksum, commands, reward counts, screenshots, and relevant logs.
Release approval remains pending while any required case is failed, blocked,
or not executed.

## Manual gameplay results for 563e4ca

This table applies only to the verified
`target/1MB-LootChest-v2.5.9.3-230-CMI-j25-26.3.jar`, built from
`563e4ca23c34f9a33dd54039653fe0d96f508fba` with `source.dirty=false`, SHA-256
`ae6aad1457388f12e79738dd96a84685fa90bda67a7196f497b5166540a775a6`.
The fixture target is isolated Paper 26.3 build 207 on installed Oracle Java 27.
The automated results above do not complete any manual gameplay row.

No manual gameplay run has been recorded. A blank result or tester/date cell
means **not run / not recorded**, not a pass. The four prefilled **BLOCKED**
results describe prerequisite gaps already identified in the plan; they are
not observed gameplay failures. All manual approval items remain outstanding.

Use only these recorded result values:

- **PASS:** The case was executed against this exact JAR, every listed action
  and variant met the plan's expected results, and evidence is linked.
- **FAIL:** An executed action differed from the expected result. Record the
  reproduction steps, expected and actual behavior, and evidence.
- **BLOCKED:** A prerequisite prevents execution or completion. Record the
  specific blocker and what is needed to resume the case.

Step numbers refer to the [manual test plan](#step-by-step-manual-gameplay-test-plan-for-563e4ca).
For each run, record the tester and date/time with timezone, client edition and
version, fixture/runtime/plugin versions, exact JAR checksum, commands, reward
counts, and links to screenshots and logs. Keep prior failure evidence when
recording a retest. For grouped cases, record each variant or action separately
in the linked evidence; do not mark the group PASS after testing only a subset.
Repeat player-facing cases on Bedrock and keep those results separate from Java
results, using additional rows if needed.

| Case | Plan step and manual check | Client | Result | Tester / date | Evidence, notes, or blocker |
| --- | --- | --- | --- | --- | --- |
| 01 | 1: Candidate identity and initial `/lc info`, `/lc list`, `/lc audit` baseline | Java | | | Capture the reported build/source and preserve the original-fixture baseline. |
| 02 | 2: Administrator and non-OP roles; UUID-targeted temporary permission if needed | Java | | | Record effective permissions, absence of ordinary-player bypasses, and any session-created grant. |
| 03 | 2 and all player-facing cases: Floodgate/Bedrock identity, permissions, and gameplay | Bedrock | BLOCKED | | Current fixture has no Geyser/Floodgate setup. Requires a suitable fixture and Bedrock account; preserve the username prefix and actual server UUID. |
| 04 | 3: Deterministic `qa563_chest` creation, editor, reward chances, and configuration | Java | | | Record the template, 100% slot chances, fixed location, and targeted audit output. |
| 05 | 4: Public info and denied create/edit/give/respawn/reload commands | Java | | | Capture each command result and confirm denied commands have no side effects. |
| 06 | 5: Partial collection, empty close, survival break, forced and timed respawn | Java | | | Record reward/drop totals and respawn timing; count empty-container drops separately. |
| 07 | 6: Chest, trapped chest, and barrel lifecycle and type menu | Java | | | Record create/edit/open/collect/break/respawn/reload for all three types. |
| 08 | 6: Uncolored and all 16 colored shulker variants | Java | | | Record the full lifecycle for each of the 17 variants. |
| 09 | 6: All eight copper oxidation/wax variants | Java | | | Record the full lifecycle for each combination of oxidation state and wax state. |
| 10 | 6: Registered copper oxidation/wax changes and reload | Java | | | Include natural oxidation, post-reload audit, and effect placement/cleanup. |
| 11 | 7: Menu controls and all inventory input paths | Java | | | Cover click, shift-click, drag, double-click, hotbar, offhand, drop, and creative actions; distinguish intentional contents edits from control-icon extraction. |
| 12 | 7: Close, quit, reconnect, and stale editor after reload | Java | | | Demonstrate that an old session cannot overwrite a fresh saved edit or affect another box. |
| 13 | 8: `/lc give` with empty and full inventories | Java | | | Record recipient inventory plus ground totals for each deliberate reward roll and check existing items remain intact. |
| 14 | 8: Third-party metadata, components, nested contents, and template isolation | Java | | | Compare original template, delivered copy, modified copy, and fresh reward. |
| 15 | 8: AutoSell enabled and disabled | Java; repeat on Bedrock | BLOCKED | | AutoSell is absent from the current fixture. Requires a suitable integration fixture and evidence for both settings. |
| 16 | 9: Temporary post-spawn protection and expiry | Java | | | Record denied open/break attempts, unchanged rewards/container, and access after expiry. |
| 17 | 9: Bolt private lock with an untrusted non-OP player | Java | | | Compare audit before/after denied open/break; confirm no delivery, removal, or respawn scheduling. |
| 18 | 10: Particles, hologram names/placement/cleanup, and `ICON:` versus `SICON:` | Java | | | Capture visuals before and after edits/respawns and check for duplicate or orphaned effects. |
| 19 | 10: List/audit clicks, hover text, colors, and CMILib clickable message | Java | | | Record editor/teleport actions and the message click running `/lc info`. |
| 20 | 10: CMI hologram reload recovery and stationary `/lc reload` | Java | | | Confirm one restored display per box and client refresh while standing still. |
| 21 | 10: Hologram countdown with timer display temporarily enabled | Java | | | Record countdown progression and restoration of the original configuration. |
| 22 | 11: Simultaneous looting, rapid clicks, and competing open/break actions | Two Java players; repeat with Bedrock | | | Reconcile both inventories, chest, and ground against each intentional spawn's contents. |
| 23 | 11: Disconnect, kick, death, and world-change interruptions | Java | | | Record each interruption, reconnect/return behavior, reward totals, and final audit. |
| 24 | 11: Shutdown during reload and recovery on a disposable copy | Java / console | | | Record shutdown/recovery logs, saved definitions/templates, and post-recovery info/audit. |
| 25 | 11: Forced termination between persistence stages | Java / console | BLOCKED | | Requires a prepared disposable copy, recoverable backup, controlled failure point, and recovery procedure before execution. |
| 26 | 11: Disk-full failure and recovery | Java / console | BLOCKED | | Requires a disposable, size-limited environment with backups and a recovery procedure; never fill the host filesystem. |
| 27 | 12: Test fixtures, locks, temporary permission, and configuration cleanup | Java / console | | | Compare final list/audit to baseline; confirm original fixtures remain and session-created changes are removed. |

Release approval remains pending until every required case and client variant
has a recorded PASS with evidence. Blank results and BLOCKED cases remain
unfinished; no PASS or FAIL gameplay outcome is asserted by adding this table.
