package fr.black_eyes.lootchest.integrations;

import static fr.black_eyes.lootchest.integrations.AutoSellGuard.Result.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import com.onemb.cmiapi.lib.RewardDeliveryDecision;
import com.onemb.cmiapi.lib.RewardDeliveryMode;

public class AutoSellGuardTest {
    private final List<String> warnings = new ArrayList<>();
    private final AtomicReference<Plugin> current = new AtomicReference<>();
    private final AtomicBoolean mainThread = new AtomicBoolean(true);
    private final AutoSellGuard guard = new AutoSellGuard(current::get, mainThread::get, warnings::add);

    @Test
    void absentOrDisabledAutoSellKeepsStandaloneCollectionWorking() {
        assertEquals(ALLOWED, guard.check(player(UUID.randomUUID())));
        Fixture fixture = new Fixture();
        fixture.active = false;
        current.set(fixture.plugin());
        assertEquals(ALLOWED, guard.check(player(UUID.randomUUID())));
        assertEquals(0, fixture.previews);
    }

    @Test
    void checksCurrentUuidStateOnEveryActionAndNeverRequestsAutomaticDelivery() {
        Fixture fixture = install();
        for (UUID uuid : List.of(UUID.fromString("631e3896-da2a-4077-974b-d047859d76bc"),
                UUID.fromString("00000000-0000-0000-0009-000000000123"))) {
            Player player = player(uuid);
            fixture.on = true;
            int before = fixture.prepares;
            assertEquals(ENABLED, guard.check(player));
            assertEquals(before, fixture.prepares, "Read-only denial must never turn AutoSell off");
            assertEquals(uuid, fixture.recipient);
            fixture.on = false;
            assertEquals(ALLOWED, guard.check(player));
            fixture.on = true;
            assertEquals(ENABLED, guard.check(player), "An already open chest must not reuse an earlier allow");
            assertEquals(uuid, fixture.recipient);
        }
    }

    @Test
    void pendingSafeOffOrInFlightSaleStaysBlockedUntilARecheckAllowsIt() {
        Fixture fixture = install();
        Player player = player(UUID.randomUUID());
        CompletableFuture<Void> pending = new CompletableFuture<>();
        fixture.prepared = new RewardDeliveryDecision(false, false, "pending", "", "", pending);
        assertEquals(BUSY, guard.check(player));
        pending.complete(null);
        assertEquals(BUSY, guard.check(player), "A completed future is not delivery permission");
        fixture.prepared = decision(true);
        assertEquals(ALLOWED, guard.check(player));
    }

    @Test
    void invalidApiAndExceptionsFailClosedAndCanRecover() {
        Fixture fixture = install();
        Player player = player(UUID.randomUUID());
        fixture.fail = true;
        assertEquals(UNAVAILABLE, guard.check(player));
        assertEquals(UNAVAILABLE, guard.check(player));
        assertEquals(1, warnings.size());
        fixture.fail = false;
        assertEquals(ALLOWED, guard.check(player));
        fixture.prepared = null;
        assertEquals(UNAVAILABLE, guard.check(player));
        current.set((Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[] {Plugin.class},
                (proxy, method, args) -> method.getName().equals("isEnabled") ? true : null));
        assertEquals(UNAVAILABLE, guard.check(player));
        install();
        assertEquals(ALLOWED, guard.check(player), "A replacement plugin must reconnect its public API");
    }

    @Test
    void offThreadCheckNeverTouchesTheProviderOrPlayer() {
        Fixture fixture = install();
        mainThread.set(false);
        assertEquals(UNAVAILABLE, guard.check(null));
        assertEquals(0, fixture.previews);
    }

    @Test
    void unexpectedPreviewMutationAndPreparedStateChangesNeverPermitDelivery() {
        Fixture fixture = install();
        Player player = player(UUID.randomUUID());
        fixture.previewOverride = new RewardDeliveryDecision(true, true, "changed", "", "", null);
        assertEquals(UNAVAILABLE, guard.check(player));
        fixture.previewOverride = null;
        fixture.prepared = new RewardDeliveryDecision(true, true, "changed", "", "", null);
        assertEquals(BUSY, guard.check(player));
    }

    private Fixture install() {
        Fixture fixture = new Fixture();
        current.set(fixture.plugin());
        return fixture;
    }

    public interface Contract {
        RewardDeliveryDecision preview(Player player);
        RewardDeliveryDecision prepare(Player player, RewardDeliveryMode mode, String source);
    }

    private static final class Fixture {
        boolean active = true, on, fail;
        int previews, prepares;
        UUID recipient;
        RewardDeliveryDecision prepared = decision(true), previewOverride;

        Plugin plugin() {
            return (Plugin) Proxy.newProxyInstance(Contract.class.getClassLoader(),
                    new Class<?>[] {Plugin.class, Contract.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "isEnabled" -> active;
                        case "preview" -> {
                            previews++;
                            if (fail) throw new IllegalStateException("fixture provider failure");
                            recipient = ((Player) args[0]).getUniqueId();
                            yield previewOverride != null ? previewOverride : decision(!on);
                        }
                        case "prepare" -> {
                            prepares++;
                            assertEquals(RewardDeliveryMode.MANUAL_CLAIM, args[1]);
                            assertEquals("LootChest", args[2]);
                            yield prepared;
                        }
                        default -> throw new UnsupportedOperationException(method.toString());
                    });
        }
    }

    private static Player player(UUID uuid) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    throw new AssertionError("No player name lookup or external identity query: " + method);
                });
    }

    private static RewardDeliveryDecision decision(boolean allowed) {
        return new RewardDeliveryDecision(allowed, false, "", "", "", null);
    }
}
