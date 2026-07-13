package fr.black_eyes.lootchest.falleffect;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Paper 26.2 packet implementation for making an invisible armorstand fall from the sky.
 */
@SuppressWarnings("unused")
public final class Fallv_26_2 implements IFallPacket {
    private final ClientboundAddEntityPacket spawnPacket;
    private final ClientboundSetEntityDataPacket dataPacket;
    private final ClientboundSetEquipmentPacket equipmentPacket;
    private final ClientboundMoveEntityPacket motionPacket;
    private final ArmorStand armorstand;
    private final Location startLocation;
    private final int height;
    private final double speed;
    private static ItemStack headItem;
    private long counter;
    private static final short SPEED_ONE_BLOCK_PER_SECOND = 410;
    private static final long COUNTER_ONE_BLOCK = 10;
    private static final short SPEED_MULTIPLIER = 31;
    private final JavaPlugin instance;

    @Override
    public Location getLocation() {
        Location loc = startLocation.clone();
        loc.setY(loc.getY() - (height - ((counter / (COUNTER_ONE_BLOCK / (this.speed * SPEED_MULTIPLIER))) - 3)));
        return loc;
    }

    public Fallv_26_2(Location loc, Material headItem, int height, double speed, JavaPlugin plugin) {
        this.instance = plugin;
        this.speed = speed;
        this.height = height;
        this.startLocation = loc;
        @SuppressWarnings("deprecation")
        MinecraftServer server = MinecraftServer.getServer();

        org.bukkit.World world = loc.getWorld();
        String worldName = (world != null) ? world.getName() : null;
        ServerLevel level = StreamSupport.stream(server.getAllLevels().spliterator(), false)
                .filter(candidate -> candidate.getWorld().getName().equals(worldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown world for falling package: " + worldName));

        ArmorStand stand = new ArmorStand(level, loc.getX(), loc.getY(), loc.getZ());
        stand.setInvisible(true);
        stand.setNoBasePlate(true);
        armorstand = stand;

        List<Pair<EquipmentSlot, ItemStack>> equipmentList = Collections.singletonList(
                new Pair<>(EquipmentSlot.HEAD, getNmsItemStackFromMaterial(headItem))
        );

        equipmentPacket = new ClientboundSetEquipmentPacket(stand.getId(), equipmentList);
        spawnPacket = new ClientboundAddEntityPacket(
                stand.getId(),
                UUID.randomUUID(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                stand.getType(),
                0,
                new Vec3(0, 0, 0),
                0.0);
        dataPacket = new ClientboundSetEntityDataPacket(stand.getId(), stand.getEntityData().getNonDefaultValues());

        short newSpeed = (short) (this.speed * SPEED_MULTIPLIER * SPEED_ONE_BLOCK_PER_SECOND);
        counter = (int) ((COUNTER_ONE_BLOCK / (this.speed * SPEED_MULTIPLIER)) * (height + 3));
        motionPacket = new ClientboundMoveEntityPacket.Pos(
                armorstand.getId(),
                (short) 0,
                (short) -newSpeed,
                (short) 0,
                true
        );
    }

    @Override
    public void sendPacketToAll() {
        @SuppressWarnings("deprecation")
        MinecraftServer server = MinecraftServer.getServer();
        Stream<ServerPlayer> players = server.getPlayerList().getPlayers().stream();
        players.forEach(player -> {
            if (!player.getBukkitEntity().getWorld().getName().equals(Objects.requireNonNull(startLocation.getWorld()).getName())) {
                return;
            }
            if (player.distanceTo(armorstand) > 100) {
                return;
            }
            player.connection.send(spawnPacket);
            player.connection.send(dataPacket);
            player.connection.send(equipmentPacket);
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.connection.send(motionPacket);
                    counter--;
                    if (counter <= 0) {
                        cancel();
                        removePacketToAll();
                    }
                }
            }.runTaskTimer(instance, 0, 2L);
        });
    }

    @Override
    public void removePacketToAll() {
        @SuppressWarnings("deprecation")
        MinecraftServer server = MinecraftServer.getServer();
        Stream<ServerPlayer> players = server.getPlayerList().getPlayers().stream();
        players.forEach(player -> player.connection.send(new ClientboundRemoveEntitiesPacket(armorstand.getId())));
    }

    public ItemStack getNmsItemStackFromMaterial(Material material) {
        String materialKey = "minecraft:" + material.name().toLowerCase(Locale.ROOT);
        String itemKey = "item." + materialKey.replace(":", ".");
        String blockKey = "block." + materialKey.replace(":", ".");
        if (headItem != null && (Objects.requireNonNull(headItem.getItem().getDescriptionId()).equals(itemKey)
                || headItem.getItem().getDescriptionId().equals(blockKey))) {
            return headItem;
        }
        for (Item item : Arrays.stream(Items.class.getFields()).map(field -> {
            try {
                return (Item) field.get(null);
            } catch (IllegalArgumentException | IllegalAccessException | ClassCastException ignored) {
            }
            return null;
        }).toArray(Item[]::new)) {
            if (item == null) {
                continue;
            }
            if (Objects.requireNonNull(item.getDescriptionId()).equals(itemKey) || item.getDescriptionId().equals(blockKey)) {
                headItem = new ItemStack(item);
                return headItem;
            }
        }
        return ItemStack.EMPTY;
    }
}
