package fr.black_eyes.lootchest.particles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

class ParticleCatalogTest {
    @Test
    void exposesExactlyThePayloadFreePaperParticles() {
        ParticleCatalog catalog = new ParticleCatalog("FLAME", warning -> {
        });
        Set<Particle> expected = Arrays.stream(Particle.values())
                .filter(ParticleCatalog::doesNotRequireData)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Particle.class)));
        Set<Particle> actual = EnumSet.copyOf(catalog.getSupportedParticles().values());

        assertEquals(expected, actual);
        assertTrue(actual.stream().allMatch(ParticleCatalog::doesNotRequireData));
        assertFalse(catalog.getSupportedParticles().containsKey(Particle.DUST.name()));
    }

    @Test
    void resolvesNamespacedNamesAndLegacyAliases() {
        ParticleCatalog catalog = new ParticleCatalog("minecraft:flame", warning -> {
        });

        assertEquals(Particle.FLAME, catalog.getFallback());
        assertEquals(Particle.TOTEM_OF_UNDYING, catalog.find(" minecraft:totem ").orElseThrow());
        assertEquals(Particle.ENCHANTED_HIT, catalog.find("crit_magic").orElseThrow());
    }

    @Test
    void invalidOrTypedFallbackUsesFlameAndWarns() {
        List<String> warnings = new ArrayList<>();

        ParticleCatalog catalog = new ParticleCatalog("DUST", warnings::add);

        assertEquals(Particle.FLAME, catalog.getFallback());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("DUST"));
    }

    @Test
    void unavailableSavedParticleFallsBackAndWarnsOnlyOnce() {
        List<String> warnings = new ArrayList<>();
        ParticleCatalog catalog = new ParticleCatalog("FLAME", warnings::add);

        assertEquals(Particle.FLAME, catalog.resolveOrFallback("removed_particle", "LootChest one"));
        assertEquals(Particle.FLAME, catalog.resolveOrFallback("REMOVED_PARTICLE", "LootChest two"));
        assertEquals(Particle.FLAME, catalog.resolveOrFallback("DUST", "LootChest three"));

        assertEquals(2, warnings.size());
        assertTrue(warnings.getFirst().contains("removed_particle"));
        assertTrue(warnings.get(1).contains("DUST"));
    }
}
