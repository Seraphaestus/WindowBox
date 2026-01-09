package amaryllis.window_box.tree;

import amaryllis.window_box.WindowBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.OptionalInt;

public abstract class CustomLeaves extends LeavesBlock {

    // Extended interpretation of log adjacency for custom trees -> adjacent if any block vertices touch

    public static final TagKey<Block> SUPPORTS_LEAVES = TagKey.create(Registries.BLOCK, WindowBox.RL("supports_leaves"));

    protected ParticleOptions particle = null;

    public CustomLeaves() {
        super(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES));
    }

    protected int getParticleRarity() { return 0; }
    protected ParticleOptions fetchParticle() { return null; }

    protected abstract BlockPos[] getAdjacentPositions();

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (getParticleRarity() <= 0 || random.nextInt(getParticleRarity()) != 0) return;
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (isFaceFull(belowState.getCollisionShape(level, below), Direction.UP)) return;
        if (particle == null) particle = fetchParticle();
        if (particle == null) return;
        ParticleUtils.spawnParticleBelow(level, pos, random, particle);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlockAndUpdate(pos, updateDistance(state, level, pos));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        BlockState state = defaultBlockState()
                .setValue(PERSISTENT, true)
                .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
        return updateDistance(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        int distance = getDistanceAt(facingState, true) + 1;
        if (distance != 1 || state.getValue(DISTANCE) != distance) level.scheduleTick(pos, this, 1);
        return state;
    }

    public BlockState updateDistance(BlockState state, LevelAccessor level, BlockPos pos) {
        var mPos = new BlockPos.MutableBlockPos();

        int distance = 7;
        for (BlockPos offset: getAdjacentPositions()) {
            boolean isDirect = Math.abs(offset.getX()) + Math.abs(offset.getY()) + Math.abs(offset.getZ()) <= 1;
            mPos.setWithOffset(pos, offset.getX(), offset.getY(), offset.getZ());
            distance = Math.min(distance, getDistanceAt(level.getBlockState(mPos), isDirect) + 1);
            if (distance == 1) break;
        }

        return state.setValue(DISTANCE, distance);
    }

    protected static int getDistanceAt(BlockState neighbor, boolean isDirect) {
        return getOptionalDistanceAt(neighbor, isDirect).orElse(7);
    }

    // If not a direct adjacent, only look at logs (and wood stairs/slabs via window_box:supports_leaves)
    public static OptionalInt getOptionalDistanceAt(BlockState state, boolean isDirect) {
        if (state.is(SUPPORTS_LEAVES)) return OptionalInt.of(0);
        if (isDirect && state.hasProperty(DISTANCE)) return OptionalInt.of(state.getValue(DISTANCE));
        return OptionalInt.empty();
    }
}
