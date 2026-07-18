package fr.black_eyes.lootchest.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.ChestSnapshot;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.ChestReport;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.ContainerState;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.IssueCode;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.Report;

class LifecycleAuditTest {

    @Test
    void presentAndWaitingForRespawnCanBothBeHealthy() {
        ChestSnapshot present = snapshot(
                "present",
                "world:1:2:3",
                ContainerState.PRESENT,
                true,
                true,
                true,
                true,
                true,
                true,
                true);
        ChestSnapshot absent = snapshot(
                "absent",
                "world:4:5:6",
                ContainerState.ABSENT,
                true,
                true,
                false,
                true,
                false,
                true,
                true);

        Report report = LifecycleAudit.inspect(List.of(present, absent), 2);

        assertTrue(report.clean());
        assertEquals(1, report.present());
        assertEquals(1, report.absent());
        assertEquals(0, report.wrongType());
        assertEquals(0, report.unavailable());
    }

    @Test
    void reportsDuplicatesWrongContainersAndStaleRuntimeState() {
        ChestSnapshot first = snapshot(
                "first",
                "world:1:2:3",
                ContainerState.WRONG_TYPE,
                false,
                true,
                true,
                true,
                true,
                true,
                false);
        ChestSnapshot second = snapshot(
                "second",
                "world:1:2:3",
                ContainerState.PRESENT,
                true,
                false,
                false,
                false,
                false,
                true,
                true);

        Report report = LifecycleAudit.inspect(List.of(first, second), 1);
        Set<IssueCode> codes = report.findings().stream()
                .map(LifecycleAudit.Finding::code)
                .collect(Collectors.toSet());

        assertTrue(codes.contains(IssueCode.DUPLICATE_LOCATION));
        assertTrue(codes.contains(IssueCode.INDEX_SIZE));
        assertTrue(codes.contains(IssueCode.INDEX_MISMATCH));
        assertTrue(codes.contains(IssueCode.WRONG_CONTAINER));
        assertTrue(codes.contains(IssueCode.STALE_HOLOGRAM));
        assertTrue(codes.contains(IssueCode.STALE_PARTICLE));
        assertTrue(codes.contains(IssueCode.MISSING_RESPAWN_TASK));
    }

    @Test
    void reportsMissingVisualsAndUnexpectedRespawnTask() {
        ChestSnapshot snapshot = snapshot(
                "visuals",
                "world:1:2:3",
                ContainerState.PRESENT,
                true,
                true,
                false,
                true,
                false,
                false,
                true);

        Report report = LifecycleAudit.inspect(List.of(snapshot), 1);
        Set<IssueCode> codes = report.findings().stream()
                .map(LifecycleAudit.Finding::code)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        IssueCode.MISSING_HOLOGRAM,
                        IssueCode.MISSING_PARTICLE,
                        IssueCode.UNEXPECTED_RESPAWN_TASK),
                codes);
    }

    @Test
    void unavailableChunksDoNotProduceVisualFalsePositives() {
        ChestSnapshot snapshot = snapshot(
                "unavailable",
                "world:1:2:3",
                ContainerState.UNAVAILABLE,
                true,
                true,
                false,
                true,
                false,
                true,
                true);

        Report report = LifecycleAudit.inspect(List.of(snapshot), 1);

        assertTrue(report.clean());
        assertEquals(1, report.unavailable());
    }

    @Test
    void targetedAuditChecksOneChestWithoutGlobalIndexSizeNoise() {
        ChestSnapshot snapshot = snapshot(
                "target",
                "world:1:2:3",
                ContainerState.PRESENT,
                true,
                true,
                true,
                true,
                true,
                true,
                true);

        ChestReport report = LifecycleAudit.inspect(snapshot);

        assertTrue(report.clean());
        assertEquals(snapshot, report.snapshot());
    }

    private static ChestSnapshot snapshot(
            String name,
            String locationKey,
            ContainerState state,
            boolean indexMatches,
            boolean hologramExpected,
            boolean hologramActive,
            boolean particleExpected,
            boolean particleActive,
            boolean respawnExpected,
            boolean respawnActive) {
        return new ChestSnapshot(
                name,
                locationKey,
                "world 1, 2, 3",
                state,
                "CHEST",
                state == ContainerState.WRONG_TYPE ? "STONE" : "CHEST",
                indexMatches,
                hologramExpected,
                hologramActive,
                particleExpected,
                particleActive,
                respawnExpected,
                respawnActive);
    }
}
