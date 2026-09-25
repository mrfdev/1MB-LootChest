# Release Process

`master` receives only manually approved builds. The current upgrade candidate
targets Paper 26.3; the historical live approval remains Paper 26.2. Development and
candidate testing happen on a `codex/<feature>` branch.

## Candidate Checklist

1. Start from current `master` and create a feature branch. For this upgrade,
   continue the preserved build-228 snapshot on `codex/paper-26.3`.
2. Increment `revision` for a semantic release and increment `buildNumber`
   exactly once in the root `pom.xml`. This release reserves version `2.5.9.3`
   and build `229`. Reuse that number after failures.
3. Implement and locally verify the focused change.
4. Commit the candidate source on the feature branch.
5. Build from that clean commit with JDK 25.0.4.1. Set
   `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.0.4.1.jdk/Contents/Home`
   and prepend `$JAVA_HOME/bin` to `PATH`, then run `mvn clean verify` from the
   repository root. Keep the Java compiler release/source/target at 25 and Paper
   at 26.3 build 41 `ALPHA`, API `26.3.build.41-alpha`. Preserve historical
   artifacts and reports in `archive/` before cleaning. If unrelated working changes exist, build from a clean worktree of
   the candidate commit. The package phase runs the
   release-jar policy and fails before copying the named artifact if updater,
   metrics, DecentHolograms, proxy, NMS, falling-effect, version-adapter, or
   retired shaded configuration-framework classes are present.
6. Confirm `/lc info` reports the expected semantic version, build, source
   commit, Paper target/build/channel/API, Java target, and artifact filename
   without a `-dirty` suffix.
7. Run `./scripts/smoke-paper-26.3.py target/<candidate>.jar`. It uses the
   maintained local launcher and JDK 27, validates the pinned Paper checksum,
   checks the embedded release metadata and dependency versions, exercises
   info/help/list/reload/despawn/respawn/audit, and verifies clean shutdown and
   port release. Retain logs under `target/smoke-paper-26.3/`. Never use the
   shared test server for this smoke run. See [local setup](test-server.md).
8. Move the current approved jar into the test server's `archive/` directory and
   install the candidate as the only top-level LootChest jar.
9. Manually test create, edit, open, empty, break, respawn, reload, particles,
   holograms, and every supported container type. Automated coverage separately
   locks Paper 26.3's chest, trapped chest, barrel, 17 shulker boxes, and all
   eight copper oxidation/wax states.

Tag an automatically verified upgrade with
`1mb-lootchest-v<version>-build<build>-paper-26.3` and push the feature branch.
The `-approved` tag remains reserved for manual gameplay approval.

Do not move the candidate commit to `master` when any check is incomplete.

## Approval

After manual gameplay approval:

1. Fast-forward `master` to the exact tested candidate commit.
2. Create an annotated tag named
   `1mb-lootchest-v<version>-build<build>-approved` on that commit.
3. Push `master` and the approval tag normally.
4. Keep the approved jar and its checksum with the release record.
5. Retain the immediately previous approved jar in `archive/` as the rollback
   artifact.

The live jar, source commit reported by `/lc info`, `master` commit, and approval
tag must all identify the same source. A dirty build can be used for early
development checks, but it is never an approval candidate.

## Rollback

Stop Paper, archive the rejected/current jar, restore the immediately previous
approved jar and matching `plugins/LootChest/` backup, then start Paper and repeat
the smoke checks. Keep exactly one LootChest jar in the top-level `plugins/`
directory.
