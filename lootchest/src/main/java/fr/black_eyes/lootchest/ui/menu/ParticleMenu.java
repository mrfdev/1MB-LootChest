package fr.black_eyes.lootchest.ui.menu;

import fr.black_eyes.lootchest.Messages;

import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.Mat;
import fr.black_eyes.lootchest.LootChestUtils;
import fr.black_eyes.lootchest.particles.ParticleCatalog;
import fr.black_eyes.lootchest.ui.PagedChestUi;
import fr.black_eyes.lootchest.ui.UiHandler;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A menu to change the particle effect surrounding a loot chest
 */
public class ParticleMenu extends PagedChestUi {
	
	private final Lootchest chest;
	private final UiHandler uiHandler;

	public ParticleMenu(Lootchest chest, UiHandler uiHandler) {
		super(6, LootChestUtils.getMenuName("particles", chest.getName()));
		this.chest = chest;
		this.uiHandler = uiHandler;

		//add an empty row with only the "Disable particle" item in the mid
		for (int i = 0; i < 9; ++i) {
			if (i == 4) {
				addContent(nameItem(Mat.BARRIER, "Disable particles"), p -> changeParticle(chest, null, p));
			} else {
				addContent(null, null);
			}
		}
		//add items for all other particle effects
		List<Particle> particles = new ArrayList<>(Main.getInstance().getParticles().values());
		particles.sort(Comparator.comparing(ParticleCatalog::readableName));
		for (Particle particle : particles) {
			String lore = particle == chest.getParticle() ? Messages.get("Menu.particles.selected") : "";
			addContent(nameItem(ParticleCatalog.icon(particle), ParticleCatalog.readableName(particle), 1, lore),
					p -> changeParticle(chest, particle, p));
		}
	}
	
	private void changeParticle(Lootchest chest, Particle particle, Player player) {
		chest.setParticle(particle);
		chest.updateData();
		Location loc = chest.getParticleLocation();
		if (particle == null) {
			Main.getInstance().getPart().remove(loc);
		} else {
			Main.getInstance().getPart().put(loc, particle);
		}
		Messages.msg(player, "editedParticle", "[Chest]", chest.getName());
	}

	@Override
	public void onClose(Player player, CloseReason reason) {
		if (reason.returnsToParent()) {
			uiHandler.openUi(player, UiHandler.UiType.MAIN, chest, 2);
		}
	}
}
