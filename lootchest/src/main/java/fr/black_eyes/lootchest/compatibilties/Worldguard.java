package fr.black_eyes.lootchest.compatibilties;

import org.bukkit.Location;

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionContainer;


public class Worldguard
{
	private Worldguard() {}

   
    public static boolean isInRegion(Location loc) {
		RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
		RegionManager regions = container.get(BukkitAdapter.adapt(loc.getWorld()));
		if (regions == null) {
			return false;
		}
		ApplicableRegionSet set = regions.getApplicableRegions(
				BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
		return set.size() > 0;

    }
}
