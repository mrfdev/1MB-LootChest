package fr.black_eyes.lootchest.particles;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Builds LootChest's particle choices from the Paper API available at runtime. */
public final class ParticleCatalog {

    private static final Map<String, String> LEGACY_ALIASES = Map.ofEntries(
            Map.entry("CRIT_MAGIC", "ENCHANTED_HIT"),
            Map.entry("DRIP_LAVA", "DRIPPING_LAVA"),
            Map.entry("DRIP_WATER", "DRIPPING_WATER"),
            Map.entry("ENCHANTMENT_TABLE", "ENCHANT"),
            Map.entry("EXPLOSION_HUGE", "EXPLOSION_EMITTER"),
            Map.entry("EXPLOSION_LARGE", "EXPLOSION"),
            Map.entry("EXPLOSION_NORMAL", "POOF"),
            Map.entry("FIREWORKS_SPARK", "FIREWORK"),
            Map.entry("GUST_DUST", "SMALL_GUST"),
            Map.entry("GUST_EMITTER", "GUST_EMITTER_SMALL"),
            Map.entry("MOB_APPEARANCE", "ELDER_GUARDIAN"),
            Map.entry("SLIME", "ITEM_SLIME"),
            Map.entry("SMOKE_LARGE", "LARGE_SMOKE"),
            Map.entry("SMOKE_NORMAL", "SMOKE"),
            Map.entry("SNOWBALL", "ITEM_SNOWBALL"),
            Map.entry("SNOW_SHOVEL", "ITEM_SNOWBALL"),
            Map.entry("SPELL_WITCH", "WITCH"),
            Map.entry("SUSPENDED_DEPTH", "UNDERWATER"),
            Map.entry("TOTEM", "TOTEM_OF_UNDYING"),
            Map.entry("TOWN_AURA", "MYCELIUM"),
            Map.entry("VILLAGER_ANGRY", "ANGRY_VILLAGER"),
            Map.entry("VILLAGER_HAPPY", "HAPPY_VILLAGER"),
            Map.entry("WATER_DROP", "RAIN"),
            Map.entry("WATER_SPLASH", "SPLASH")
    );

    private final Map<String, Particle> supportedParticles;
    private final Consumer<String> warningSink;
    private final Set<String> warnedValues = new HashSet<>();
    private final Particle fallback;

    public ParticleCatalog(String configuredFallback, Consumer<String> warningSink) {
        this.warningSink = warningSink;

        LinkedHashMap<String, Particle> detected = new LinkedHashMap<>();
        Arrays.stream(Particle.values())
                .filter(ParticleCatalog::doesNotRequireData)
                .sorted((left, right) -> left.name().compareTo(right.name()))
                .forEach(particle -> detected.put(particle.name(), particle));
        supportedParticles = Collections.unmodifiableMap(detected);

        Particle configured = find(configuredFallback).orElse(null);
        if (configured == null && configuredFallback != null && !configuredFallback.isBlank()) {
            warningSink.accept("Configured particle fallback '" + configuredFallback
                    + "' is unavailable or requires extra data on this Paper version.");
        }
        fallback = configured != null
                ? configured
                : find("FLAME").orElseGet(() -> detected.values().stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("Paper did not expose any payload-free particles")));
    }

    public Map<String, Particle> getSupportedParticles() {
        return supportedParticles;
    }

    public Particle getFallback() {
        return fallback;
    }

    public Particle resolveOrFallback(String requested, String source) {
        Optional<Particle> resolved = find(requested);
        if (resolved.isPresent()) {
            return resolved.get();
        }

        String displayValue = requested == null ? "<missing>" : requested;
        if (warnedValues.add(displayValue.toUpperCase(Locale.ROOT))) {
            warningSink.accept("Particle '" + displayValue + "' used by " + source
                    + " is unavailable or requires extra data; using " + fallback.name() + ".");
        }
        return fallback;
    }

    public Optional<Particle> find(String requested) {
        if (requested == null || requested.isBlank()) {
            return Optional.empty();
        }

        String normalized = requested.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        normalized = LEGACY_ALIASES.getOrDefault(normalized, normalized);
        return Optional.ofNullable(supportedParticles.get(normalized));
    }

    public void display(Particle particle, Location center, int amount, double radius,
                        double speed, Iterable<Player> players) {
        for (Player player : players) {
            if (player.getWorld() != center.getWorld()
                    || player.getLocation().distanceSquared(center) > 65536) {
                continue;
            }
            player.spawnParticle(particle, center, amount, radius, radius, radius, speed);
        }
    }

    public static boolean doesNotRequireData(Particle particle) {
        return particle.getDataType() == Void.class;
    }

    public static String readableName(Particle particle) {
        String[] words = particle.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public static Material icon(Particle particle) {
        String name = particle.name();
        if (name.contains("WATER") || name.contains("BUBBLE") || name.contains("SPLASH")
                || name.contains("RAIN") || name.contains("DOLPHIN") || name.contains("FISHING")) {
            return Material.WATER_BUCKET;
        }
        if (name.contains("LAVA")) return Material.LAVA_BUCKET;
        if (name.contains("FLAME") || name.contains("FIRE") || name.contains("CAMPFIRE")) return Material.BLAZE_POWDER;
        if (name.contains("SCULK") || name.contains("SONIC")) return Material.SCULK;
        if (name.contains("CHERRY") || name.contains("LEAVES")) return Material.CHERRY_LEAVES;
        if (name.contains("SOUL")) return Material.SOUL_LANTERN;
        if (name.contains("PORTAL") || name.contains("END_ROD")) return Material.ENDER_EYE;
        if (name.contains("VILLAGER")) return Material.EMERALD;
        if (name.contains("TRIAL") || name.contains("OMINOUS") || name.contains("VAULT")) return Material.TRIAL_KEY;
        if (name.contains("COPPER") || name.contains("SCRAPE") || name.contains("WAX")) return Material.COPPER_INGOT;
        if (name.contains("SMOKE") || name.contains("ASH") || name.contains("POOF")) return Material.GRAY_DYE;
        if (name.contains("SNOW")) return Material.SNOWBALL;
        if (name.contains("ENCHANT") || name.contains("WITCH") || name.contains("SPELL")) return Material.ENCHANTED_BOOK;
        if (name.contains("EXPLOSION")) return Material.TNT;
        if (name.contains("HEART") || name.contains("DAMAGE")) return Material.RED_DYE;
        if (name.contains("GLOW")) return Material.GLOW_INK_SAC;
        if (name.contains("NOTE")) return Material.NOTE_BLOCK;
        return Material.NETHER_STAR;
    }
}
