package fr.black_eyes.lootchest.compatibilties;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GriefPreventions  {
	public static boolean isInGriefPreventionClaim(Location loc) {
		Plugin grief = Bukkit.getServer().getPluginManager().getPlugin("GriefPrevention");
        if (grief == null) {
            return false;
        }

        try {
            Field dataStoreField = grief.getClass().getField("dataStore");
            Object dataStore = dataStoreField.get(grief);
            Method getClaimAt = dataStore.getClass().getMethod("getClaimAt", Location.class, boolean.class, Long.class);
            return getClaimAt.invoke(dataStore, loc, true, null) != null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
	}

	private GriefPreventions() {
		throw new IllegalStateException("Utility class");
	}
}
