package fr.black_eyes.lootchest.integrations;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.Messages;

/** Optional bridge to 1MB-Library's public RewardDeliveryGuard contract. */
public final class AutoSellGuard {
    public static final String PLUGIN_NAME = "1MB-CMIAPI-AutoSell";
    private final Supplier<Plugin> lookup;
    private final BooleanSupplier primaryThread;
    private final Consumer<String> warning;
    private Plugin provider;
    private Bridge bridge;
    private boolean failureReported;
    private final Map<UUID, Long> pickupNotices = new HashMap<>();

    public AutoSellGuard() {
        this(() -> Bukkit.getPluginManager().getPlugin(PLUGIN_NAME), Bukkit::isPrimaryThread,
                message -> Main.getInstance().getLogger().warning(message));
    }

    AutoSellGuard(Supplier<Plugin> lookup, BooleanSupplier primaryThread, Consumer<String> warning) {
        this.lookup = lookup;
        this.primaryThread = primaryThread;
        this.warning = warning;
    }

    public Result check(Player player) {
        // Neither Bukkit nor AutoSell's authoritative player state is safe off-thread.
        if (!primaryThread.getAsBoolean()) return Result.UNAVAILABLE;
        try {
            Plugin current = lookup.get();
            if (current == null || !current.isEnabled()) return Result.ALLOWED;
            if (current != provider) {
                provider = current;
                bridge = null;
                failureReported = false;
            }
            if (bridge == null) bridge = Bridge.connect(current);
            Result result = bridge.check(current, player);
            failureReported = false;
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!failureReported) {
                warning.accept("AutoSell reward safety is unavailable; LootChest collection is blocked until "
                        + "the integration recovers. Check 1MB-Library/AutoSell compatibility ("
                        + failure.getClass().getSimpleName() + ").");
                failureReported = true;
            }
            return Result.UNAVAILABLE;
        }
    }

    public boolean allowCollection(Player player) {
        return allowCollection(player, "AutoSellCannotCollect");
    }

    public boolean allowCollection(Player player, String enabledMessage) {
        Result result = check(player);
        if (result == Result.ALLOWED) return true;
        notifyBlocked(player, result, enabledMessage);
        return false;
    }

    public boolean allowPickup(Player player) {
        Result result = check(player);
        if (result == Result.ALLOWED) {
            forget(player.getUniqueId());
            return true;
        }
        long now = System.nanoTime();
        Long previous = pickupNotices.get(player.getUniqueId());
        if (previous == null || now - previous >= 2_000_000_000L) {
            pickupNotices.put(player.getUniqueId(), now);
            notifyBlocked(player, result, "AutoSellCannotCollect");
        }
        return false;
    }

    public void forget(UUID uuid) {
        pickupNotices.remove(uuid);
    }

    private static void notifyBlocked(Player player, Result result, String enabledMessage) {
        Messages.msg(player, switch (result) {
            case ENABLED -> enabledMessage;
            case BUSY -> "AutoSellBusy";
            default -> "AutoSellUnavailable";
        });
    }

    public enum Result { ALLOWED, ENABLED, BUSY, UNAVAILABLE }

    /**
     * The optional library has no published Maven API. Resolve only its public,
     * capability-checked API, using its own loader; never inspect profiles or files.
     * Reconnect when the plugin instance changes and never cache a player's result.
     */
    private record Bridge(Method preview, Method prepare, Object manualClaim,
                          Method allowed, Method stateChanged, Method readiness) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        static Bridge connect(Plugin plugin) throws ReflectiveOperationException {
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> mode = Class.forName("com.onemb.cmiapi.lib.RewardDeliveryMode", false, loader);
            Class<?> decision = Class.forName("com.onemb.cmiapi.lib.RewardDeliveryDecision", false, loader);
            Method preview = plugin.getClass().getMethod("preview", Player.class);
            Method prepare = plugin.getClass().getMethod("prepare", Player.class, mode, String.class);
            if (preview.getReturnType() != decision || prepare.getReturnType() != decision) {
                throw new NoSuchMethodException("Unsupported reward-safety result contract");
            }
            return new Bridge(preview, prepare, Enum.valueOf((Class<? extends Enum>) mode, "MANUAL_CLAIM"),
                    decision.getMethod("allowed"), decision.getMethod("stateChanged"), decision.getMethod("readiness"));
        }

        Result check(Plugin plugin, Player player) throws ReflectiveOperationException {
            Object result = preview.invoke(plugin, player);
            if (result == null || !Boolean.FALSE.equals(stateChanged.invoke(result))
                    || readiness.invoke(result) != null) return Result.UNAVAILABLE;
            if (!Boolean.TRUE.equals(allowed.invoke(result))) return Result.ENABLED;
            // Preview alone misses an in-flight sale or safe-off save. Never await,
            // retry automatically, or ask AutoSell to disable itself for delivery.
            result = prepare.invoke(plugin, player, manualClaim, "LootChest");
            if (result == null) return Result.UNAVAILABLE;
            return Boolean.TRUE.equals(allowed.invoke(result))
                    && Boolean.FALSE.equals(stateChanged.invoke(result))
                    && readiness.invoke(result) == null ? Result.ALLOWED : Result.BUSY;
        }
    }
}
