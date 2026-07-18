package fr.black_eyes.lootchest.lifecycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure lifecycle consistency checks. Runtime state is captured separately so
 * this class cannot mutate Bukkit worlds, chests, displays, or tasks.
 */
public final class LifecycleAudit {
    private LifecycleAudit() {
    }

    public static Report inspect(Collection<ChestSnapshot> snapshots, int indexedEntries) {
        List<ChestSnapshot> sortedSnapshots = snapshots.stream()
                .sorted(Comparator.comparing(ChestSnapshot::name))
                .toList();
        List<Finding> findings = new ArrayList<>();

        if (indexedEntries != sortedSnapshots.size()) {
            findings.add(new Finding(
                    IssueCode.INDEX_SIZE,
                    "all",
                    "index has " + indexedEntries + " entries for "
                            + sortedSnapshots.size() + " loaded LootChests"));
        }

        Map<String, List<ChestSnapshot>> byLocation = new LinkedHashMap<>();
        for (ChestSnapshot snapshot : sortedSnapshots) {
            if (snapshot.locationKey() != null) {
                byLocation.computeIfAbsent(snapshot.locationKey(), ignored -> new ArrayList<>()).add(snapshot);
            }
        }
        for (List<ChestSnapshot> sharedLocation : byLocation.values()) {
            if (sharedLocation.size() < 2) {
                continue;
            }
            String names = sharedLocation.stream().map(ChestSnapshot::name).sorted().reduce(
                    (left, right) -> left + ", " + right).orElse("unknown");
            findings.add(new Finding(
                    IssueCode.DUPLICATE_LOCATION,
                    names,
                    "multiple definitions share " + sharedLocation.getFirst().locationDisplay()));
        }

        for (ChestSnapshot snapshot : sortedSnapshots) {
            inspectSnapshot(snapshot, findings);
        }

        findings.sort(Comparator
                .comparing((Finding finding) -> finding.code().name())
                .thenComparing(Finding::chest));

        int present = count(sortedSnapshots, ContainerState.PRESENT);
        int absent = count(sortedSnapshots, ContainerState.ABSENT);
        int wrongType = count(sortedSnapshots, ContainerState.WRONG_TYPE);
        int unavailable = count(sortedSnapshots, ContainerState.UNAVAILABLE);
        return new Report(
                sortedSnapshots.size(),
                indexedEntries,
                present,
                absent,
                wrongType,
                unavailable,
                List.copyOf(findings));
    }

    private static void inspectSnapshot(ChestSnapshot snapshot, List<Finding> findings) {
        if (!snapshot.indexMatches()) {
            findings.add(new Finding(
                    IssueCode.INDEX_MISMATCH,
                    snapshot.name(),
                    "raw index does not point to this chest at " + snapshot.locationDisplay()));
        }

        if (snapshot.containerState() == ContainerState.WRONG_TYPE) {
            findings.add(new Finding(
                    IssueCode.WRONG_CONTAINER,
                    snapshot.name(),
                    "expected " + snapshot.expectedType() + " but found " + snapshot.actualType()
                            + " at " + snapshot.locationDisplay()));
        }

        if (snapshot.containerState() == ContainerState.PRESENT) {
            compareExpectedState(
                    snapshot,
                    snapshot.hologramExpected(),
                    snapshot.hologramActive(),
                    IssueCode.MISSING_HOLOGRAM,
                    IssueCode.STALE_HOLOGRAM,
                    "hologram",
                    findings);
            compareExpectedState(
                    snapshot,
                    snapshot.particleExpected(),
                    snapshot.particleActive(),
                    IssueCode.MISSING_PARTICLE,
                    IssueCode.STALE_PARTICLE,
                    "particle entry",
                    findings);
        } else if (snapshot.containerState() != ContainerState.UNAVAILABLE) {
            if (snapshot.hologramActive()) {
                findings.add(new Finding(
                        IssueCode.STALE_HOLOGRAM,
                        snapshot.name(),
                        "hologram is active while the configured container is absent"));
            }
            if (snapshot.particleActive()) {
                findings.add(new Finding(
                        IssueCode.STALE_PARTICLE,
                        snapshot.name(),
                        "particle entry is active while the configured container is absent"));
            }
        }

        compareExpectedState(
                snapshot,
                snapshot.respawnTaskExpected(),
                snapshot.respawnTaskActive(),
                IssueCode.MISSING_RESPAWN_TASK,
                IssueCode.UNEXPECTED_RESPAWN_TASK,
                "respawn task",
                findings);
    }

    private static void compareExpectedState(
            ChestSnapshot snapshot,
            boolean expected,
            boolean active,
            IssueCode missingCode,
            IssueCode staleCode,
            String stateName,
            List<Finding> findings) {
        if (expected && !active) {
            findings.add(new Finding(
                    missingCode,
                    snapshot.name(),
                    stateName + " is expected but not active"));
        } else if (!expected && active) {
            findings.add(new Finding(
                    staleCode,
                    snapshot.name(),
                    stateName + " is active but not expected"));
        }
    }

    private static int count(List<ChestSnapshot> snapshots, ContainerState state) {
        return (int) snapshots.stream().filter(snapshot -> snapshot.containerState() == state).count();
    }

    public enum ContainerState {
        PRESENT,
        ABSENT,
        WRONG_TYPE,
        UNAVAILABLE
    }

    public enum IssueCode {
        DUPLICATE_LOCATION,
        INDEX_MISMATCH,
        INDEX_SIZE,
        MISSING_HOLOGRAM,
        MISSING_PARTICLE,
        MISSING_RESPAWN_TASK,
        STALE_HOLOGRAM,
        STALE_PARTICLE,
        UNEXPECTED_RESPAWN_TASK,
        WRONG_CONTAINER
    }

    public record ChestSnapshot(
            String name,
            String locationKey,
            String locationDisplay,
            ContainerState containerState,
            String expectedType,
            String actualType,
            boolean indexMatches,
            boolean hologramExpected,
            boolean hologramActive,
            boolean particleExpected,
            boolean particleActive,
            boolean respawnTaskExpected,
            boolean respawnTaskActive) {
        public ChestSnapshot {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(locationDisplay, "locationDisplay");
            Objects.requireNonNull(containerState, "containerState");
            Objects.requireNonNull(expectedType, "expectedType");
            Objects.requireNonNull(actualType, "actualType");
        }
    }

    public record Finding(IssueCode code, String chest, String detail) {
        public Finding {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(chest, "chest");
            Objects.requireNonNull(detail, "detail");
        }
    }

    public record Report(
            int total,
            int indexedEntries,
            int present,
            int absent,
            int wrongType,
            int unavailable,
            List<Finding> findings) {
        public Report {
            findings = List.copyOf(findings);
        }

        public boolean clean() {
            return findings.isEmpty();
        }
    }
}
