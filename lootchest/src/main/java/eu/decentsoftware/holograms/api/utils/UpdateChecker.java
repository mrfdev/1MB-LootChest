package eu.decentsoftware.holograms.api.utils;

import java.util.function.Consumer;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Disables DecentHolograms' standalone update check when it is embedded in LootChest.
 */
public final class UpdateChecker {

	public UpdateChecker(JavaPlugin plugin, int resourceId) {
		// The embedded library is released and updated as part of LootChest.
	}

	public void getVersion(Consumer<String> consumer) {
		// Intentionally disabled: DH is not installed as a standalone plugin here.
	}
}
