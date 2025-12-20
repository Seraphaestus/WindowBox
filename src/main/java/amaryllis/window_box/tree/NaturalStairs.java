package amaryllis.window_box.tree;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.function.Supplier;

public class NaturalStairs extends StairBlock {
    public static final BooleanProperty PERSISTENT = BlockStateProperties.PERSISTENT;

    public NaturalStairs(Supplier<BlockState> state, Properties properties) {
        super(state, properties);
        registerDefaultState(super.defaultBlockState().setValue(PERSISTENT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(PERSISTENT));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(PERSISTENT, true);
    }
}
