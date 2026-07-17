package fr.black_eyes.lootchest.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Deterministic migrations for legacy LootChest YAML files. */
public final class CompatibilityMigrations {
    private static final String CHANCES_LORE = "Menu.chances.lore";
    private static final String HELP = "help";

    private CompatibilityMigrations() {
    }

    public static boolean migrateConfig(FileConfiguration config) {
        boolean changed = false;

        if (config.getInt("Particles.respawn_ticks") == 5) {
            changed |= set(config, "Particles.respawn_ticks", 20);
        }
        if (config.isSet("RemoveChestAfterFirstOpenning")) {
            boolean removeAfterOpening = config.getBoolean("RemoveChestAfterFirstOpenning");
            changed |= remove(config, "RemoveChestAfterFirstOpenning");
            changed |= set(config, "RemoveChestAfterFirstOpening", removeAfterOpening);
        }

        changed |= set(config, "Fall_Effect.Enabled", false);
        changed |= remove(config, "Fall_Effect.Let_Block_Above_Chest_After_Fall");
        changed |= remove(config, "Fall_Effect.Optionnal_Color_If_Block_Is_Wool");
        changed |= remove(config, "respawn_notify.bungee_broadcast");
        return changed;
    }

    public static boolean migrateLanguage(FileConfiguration language) {
        boolean changed = false;
        String chancesLore = language.getString(CHANCES_LORE);
        if (chancesLore != null && chancesLore.contains("%")) {
            changed |= set(language, CHANCES_LORE, chancesLore.replace("%", ""));
        }

        List<String> help = new ArrayList<>(language.getStringList(HELP));
        boolean helpChanged = help.removeIf(line -> {
            String normalized = line.toLowerCase(Locale.ROOT);
            return normalized.contains("togglefall") || normalized.contains("removeallholo");
        });
        if (helpChanged) {
            changed |= set(language, HELP, help);
        }

        changed |= remove(language, "enabledFallEffect");
        changed |= remove(language, "disabledFallEffect");
        changed |= remove(language, "Menu.main.disable_fall");
        changed |= remove(language, "Menu.main.enable_fall");
        return changed;
    }

    public static boolean migrateSavedChestData(FileConfiguration data) {
        ConfigurationSection chests = data.getConfigurationSection("chests");
        if (chests == null) {
            return false;
        }

        boolean changed = false;
        for (String chestName : chests.getKeys(false)) {
            changed |= set(data, "chests." + chestName + ".fall", false);
        }
        return changed;
    }

    private static boolean set(ConfigurationSection section, String path, Object value) {
        if (Objects.equals(section.get(path), value)) {
            return false;
        }
        section.set(path, value);
        return true;
    }

    private static boolean remove(ConfigurationSection section, String path) {
        if (!section.isSet(path)) {
            return false;
        }
        section.set(path, null);
        return true;
    }
}
