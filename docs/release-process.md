# Release Process

`master` contains only manually approved Paper 26.2 builds. Development and
candidate testing happen on a `codex/<feature>` branch.

## Candidate Checklist

1. Start from current `master` and create a feature branch.
2. Increment `revision` for a semantic release and increment `buildNumber`
   exactly once in the root `pom.xml`.
3. Implement and locally verify the focused change.
4. Commit the candidate source on the feature branch.
5. Build from that clean commit with JDK 25.0.4.1. Set
   `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.0.4.1.jdk/Contents/Home`
   and prepend `$JAVA_HOME/bin` to `PATH`, then run `mvn clean verify` from the
   repository root. Keep the Java compiler release/source/target at 25 and Paper
   at 26.2. Preserve historical artifacts and reports in `archive/` before
   cleaning. If unrelated working changes exist, build from a clean worktree of
   the candidate commit. The package phase runs the
   release-jar policy and fails before copying the named artifact if updater,
   metrics, DecentHolograms, proxy, NMS, falling-effect, version-adapter, or
   retired shaded configuration-framework classes are present.
6. Confirm `/lc info` reports the expected semantic version, build, source
   commit, Paper target/build/channel/API, Java target, and artifact filename
   without a `-dirty` suffix.
7. Run the repeatable central Paper smoke test against the same exact candidate
   on JDK 25.0.4.1 and JDK 26.0.2.1 (the live runtime). Set `JAVA_HOME` to each
   JDK and prepend its `bin` directory to `PATH` before invoking
   `./scripts/smoke-paper-26.2.sh target/<candidate>.jar`. It verifies enable,
   info/help/list/audit commands, reload, despawn, respawn, clean shutdown, compatibility
   exceptions, and port release. Review the retained logs under
   `target/smoke-paper-26.2/<timestamp>/`.
   The runner must use the embedded Paper build/API; if the shared cache has
   moved ahead, use `LOOTCHEST_TEST_RUNNER` to select an isolated copy of the
   central runner with cache updates disabled and the pinned Paper jar installed.
8. Move the current approved jar into the test server's `archive/` directory and
   install the candidate as the only top-level LootChest jar.
9. Manually test create, edit, open, empty, break, respawn, reload, particles,
   holograms, and every supported container type. Automated coverage separately
   locks Paper 26.2's chest, trapped chest, barrel, 17 shulker boxes, and all
   eight copper oxidation/wax states.

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
