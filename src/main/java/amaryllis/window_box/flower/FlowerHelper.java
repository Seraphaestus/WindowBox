package amaryllis.window_box.flower;

import amaryllis.window_box.DataGen;
import amaryllis.window_box.Registry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

import static amaryllis.window_box.Registry.RegisterBlockOnly;

public class FlowerHelper {
    // Some utility functions are in CustomFlower

    public static final BlockBehaviour.Properties POTTED_FLOWER_PROPERTIES = BlockBehaviour.Properties.of()
            .instabreak().noOcclusion().pushReaction(PushReaction.DESTROY);

    public static void RegisterPottedFlower(String ID) {
        RegisterPottedFlower(ID, ID);
    }
    public static void RegisterPottedFlower(String ID, String flowerTexture) {
        RegisterBlockOnly("potted_" + ID, () -> new FlowerPotBlock(Registry.getBlock(ID), POTTED_FLOWER_PROPERTIES));
        DataGen.BlockModel("potted_" + ID, (models, _name) -> DataGen.Models.flowerPot(models, ID, flowerTexture));
        DataGen.TagBlock("potted_" + ID, BlockTags.FLOWER_POTS);
    }

    protected static FlowerPotBlock getFlowerPot() {
        return (FlowerPotBlock)Blocks.FLOWER_POT;
    }
}
