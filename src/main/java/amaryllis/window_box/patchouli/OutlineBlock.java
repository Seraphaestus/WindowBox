package amaryllis.window_box.patchouli;

import amaryllis.window_box.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class OutlineBlock {

    public static void register() {
        Registry.RegisterBlockOnly("outline_block", () -> new Block(BlockBehaviour.Properties.copy(Blocks.BARRIER)));
    }

}
