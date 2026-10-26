package fr.neatmonster.nocheatplus.compat.blocks.init.vanilla;

import org.bukkit.Material;

import fr.neatmonster.nocheatplus.compat.blocks.BlockPropertiesSetup;
import fr.neatmonster.nocheatplus.compat.blocks.init.BlockInit;
import fr.neatmonster.nocheatplus.config.WorldConfigProvider;
import fr.neatmonster.nocheatplus.logging.LogUtil;
import fr.neatmonster.nocheatplus.utilities.BlockProperties;

public class BlocksMC1_8 implements BlockPropertiesSetup {

    public BlocksMC1_8() {
        BlockInit.assertMaterialNameMatch(165, "slime");
        BlockInit.assertMaterialNameMatch(197, "door");
    }

    @Override
    public void setupBlockProperties(final WorldConfigProvider<?> worldConfigProvider) {
        BlockInit.setAs(165, Material.TNT);
        BlockInit.setAs(166, Material.BEDROCK);
        BlockInit.setAs(167, Material.TRAP_DOOR);
        BlockInit.setPropsAs(167, Material.IRON_DOOR_BLOCK);
        BlockInit.setAs(168, Material.STONE);
        BlockInit.setAs(169, Material.REDSTONE_LAMP_OFF);
        BlockInit.setAs(176, Material.SIGN_POST);
        BlockInit.setAs(177, Material.WALL_SIGN);
        BlockProperties.setBlockFlags(176, 0);
        BlockProperties.setBlockFlags(177, 0);
        BlockInit.setAs(178, Material.DAYLIGHT_DETECTOR);
        BlockInit.setAs(179, Material.SANDSTONE);
        BlockInit.setAs(180, Material.SANDSTONE_STAIRS);
        BlockInit.setAs(181, Material.DOUBLE_STEP);
        BlockInit.setAs(182, Material.STEP);
        for (int id = 183; id <= 187; id++) {
            BlockInit.setAs(id, Material.FENCE_GATE);
        }
        for (int id = 188; id <= 192; id++) {
            BlockInit.setAs(id, Material.FENCE);
        }
        for (int id = 193; id <= 197; id++) {
            BlockInit.setAs(id, Material.WOODEN_DOOR);
        }
        LogUtil.logInfo("[NoCheatPlus] Added block-info for Minecraft 1.8 blocks.");
    }
}
