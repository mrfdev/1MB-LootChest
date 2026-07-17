package fr.black_eyes.lootchest.ui.menu;

import fr.black_eyes.lootchest.Messages;

import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Mat;
import fr.black_eyes.lootchest.LootChestUtils;
import fr.black_eyes.lootchest.ui.ChestUi;
import fr.black_eyes.lootchest.ui.UiHandler;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A menu to change the material of a loot chest
 */
public class TypeMenu extends ChestUi {

	private final Lootchest chest;
	private final UiHandler uiHandler;

	public TypeMenu(Lootchest chest, UiHandler uiHandler) {
		super(4, LootChestUtils.getMenuName("type", chest.getName()));
		this.chest = chest;
		this.uiHandler = uiHandler;

		setItem(0, new ItemStack(Mat.CHEST, 1), p -> changeChestType(p, Mat.CHEST));
		setItem(1, new ItemStack(Mat.TRAPPED_CHEST, 1), p -> changeChestType(p, Mat.TRAPPED_CHEST));
		if (Mat.BARREL != Mat.CHEST) {
			setItem(2, new ItemStack(Mat.BARREL), p -> changeChestType(p, Mat.BARREL));
		}
		int cpt = 3;
		for (Material mat : Material.values()) {
			if (Mat.isCopperChest(mat)) {
				setItem(cpt, new ItemStack(mat, 1), p -> changeChestType(p, mat));
				cpt++;
			}
		}
		for (Material mat : Material.values()) {
			if (Mat.isShulkerBox(mat)) {
				setItem(cpt, new ItemStack(mat, 1), p -> changeChestType(p, mat));
				cpt++;
			}
		}
	}

	void changeChestType(Player player, Material type) {
		chest.setType(type);
		chest.updateData();
		chest.despawn();
		chest.spawn(true);
		Messages.msg(player, "editedChestType", "[Chest]", chest.getName());
	}

	@Override
	public void onClose(Player player, CloseReason reason) {
		if (reason.returnsToParent()) {
			uiHandler.openUi(player, UiHandler.UiType.MAIN, chest, 2);
		}
	}
}
