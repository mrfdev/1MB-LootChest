package fr.black_eyes.lootchest.falleffect;

import fr.black_eyes.lootchest.Messages;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.util.Vector;

import fr.black_eyes.lootchest.Main;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;



public final class FallingPackageEntity {

    private static final String FALL_ADAPTER_VERSION = "26_2";
    final World world;
    final Location startLoc;
    final Material material;
    Object blocky;
    boolean armorstand;
    final Location target;
    final double speed;
    final boolean fireworks;
    final int height;
    IFallPacket armorstandFall;
    private int counter = 0;
    
    public FallingPackageEntity(final Location loc, boolean loaded, Location target) {
    	Main main = Main.getInstance();
    	this.fireworks = Main.configs.fallEnableFireworks;
    	this.target = target;
        this.height = Main.configs.fallHeight;
    	this.armorstand = main.isUseArmorStands();
        this.blocky = null;
        this.armorstandFall = null;
        this.startLoc = loc.clone();
        this.world = loc.getWorld();
        this.material = Material.valueOf(Main.configs.fallBlock);
        this.speed = Main.configs.FALL_Speed;
        if(loaded)
            this.summon();
    }
    

	public void summon() {
		if(!this.armorstand) {
			this.blocky = spawnFallingBlock(startLoc);
		}else {	
            
            try {
                this.armorstandFall = (IFallPacket) Class.forName("fr.black_eyes.lootchest.falleffect.Fallv_" + FALL_ADAPTER_VERSION)
                        .getDeclaredConstructor(Location.class, Material.class, int.class, double.class, JavaPlugin.class)
                        .newInstance(startLoc, this.material, this.height, this.speed, Main.getInstance());
            } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | ClassNotFoundException ex) {
				Messages.log("<#f38ba8>Error while creating the falling-package armor stand packet: " + ex.getMessage());
                //ex.printStackTrace();
            }
            if (armorstandFall != null) {
                if (Main.configs.debug) {
					Messages.log("<#a6e3a1>Using falling package adapter: v_" + FALL_ADAPTER_VERSION);
                }
                armorstandFall.sendPacketToAll();
            } else {
                this.armorstand = false;
                this.blocky = spawnFallingBlock(startLoc);
            }
		}
        if(fireworks) {
            this.summonUpdateFireworks(FireworkEffect.Type.BALL_LARGE);
        }
        this.tick();
    }
    
	public Location goodLocation() {
		if(!armorstand) return ((Entity) this.blocky).getLocation().clone();
		else {
			Location loc2 = armorstandFall.getLocation().clone();
			loc2.setY(loc2.getY()+3);
			return loc2;
		}
	}

	public void tick() {
        if(blocky!=null){
            Vector v = ((Entity) blocky).getVelocity();
            v.setY(-(speed));
            ((Entity) blocky).setVelocity(v);
        }
        Location locPackage = (!this.armorstand && this.blocky != null)? ((Entity) this.blocky).getLocation() : armorstandFall.getLocation();

        if (locPackage != null && this.world.getBlockAt(LocationUtils.offset(locPackage, 0.0, -1.0, 0.0)).getType() == Material.AIR) {
            ++this.counter;
            this.world.spawnParticle(Particle.SMOKE, goodLocation(), 1, 0.1, 0.1, 0.1, 0.1);
            if (!this.armorstand && ((Entity) this.blocky).isDead()) {
                final Vector oldVelocity = ((Entity) this.blocky).getVelocity().setY(-(speed));
                this.blocky = spawnFallingBlock(locPackage);
                ((Entity) (this.blocky)).setVelocity(oldVelocity);
            }

            if (this.counter % 5 == 0 && ((locPackage.getY() - target.getY()) > 3 || counter > 100) && fireworks) {
                this.summonUpdateFireworks(FireworkEffect.Type.BALL);
            }
            if ((locPackage.getY() - target.getY()) < 1) {
                this.remove();
            } else if (counter < 100) {
                this.retick();
            } else {
                this.remove();
            }
        }
    }
    
    public void remove() {
        if(!this.armorstand) {
        	((Entity) this.blocky).remove();
        }
    }

    private FallingBlock spawnFallingBlock(Location location) {
        return world.spawn(location, FallingBlock.class,
                fallingBlock -> fallingBlock.setBlockData(material.createBlockData()));
    }
    
    private void summonUpdateFireworks(FireworkEffect.Type type) {
            final Firework fw = this.world.spawn(goodLocation(), Firework.class);
            final FireworkMeta fwm = fw.getFireworkMeta();
            fwm.addEffect(FireworkEffect.builder().with(type).withColor(Color.RED).withColor(Color.WHITE).build());
            fwm.setPower(1);
            fw.setFireworkMeta(fwm);
            Main.getInstance().getServer().getScheduler().runTaskLater(Main.getInstance(), fw::detonate, 1L);
    }

    private void retick() {
    	Main.getInstance().getServer().getScheduler().runTaskLater(Main.getInstance(), this::tick, 1L);
    }
    

}
