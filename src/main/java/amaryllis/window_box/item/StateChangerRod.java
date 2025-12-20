package amaryllis.window_box.item;

import amaryllis.window_box.DataGen;
import amaryllis.window_box.Registry;
import amaryllis.window_box.WindowBox;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.Vec3;
import vazkii.botania.api.mana.ManaItemHandler;
import vazkii.botania.client.fx.SparkleParticleData;

import javax.annotation.Nullable;

import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.Util.trySetBooleanBlockProperty;

public class StateChangerRod extends Item {

    public static final String ID = "state_changer_rod";

    public static final TagKey<Block> STAIRS_LIKE = TagKey.create(Registries.BLOCK, WindowBox.RL(ID + "/stairs_like"));
    public static final TagKey<Block> FENCE_LIKE = TagKey.create(Registries.BLOCK, WindowBox.RL(ID + "/fence_like"));
    public static final TagKey<Block> WALL_LIKE = TagKey.create(Registries.BLOCK, WindowBox.RL(ID + "/wall_like"));

    public static final int COST = 10;
    protected static final int UPDATE_FLAGS = 18; // 2 (send to client) and 16 (prevent neighbor updates)

    public static void register() {
        RegisterItem(ID, () -> new StateChangerRod(new Item.Properties().stacksTo(1)));
        DataGen.ItemModel(ID, DataGen.Models::handheld);
    }

    public StateChangerRod(Properties props) { super(props); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        ItemStack rod = context.getItemInHand();

        if (player == null) return InteractionResult.FAIL;
        if (!ManaItemHandler.instance().requestManaExactForTool(rod, player, COST, false)) return InteractionResult.FAIL;

        BlockPos pos = context.getClickedPos();
        Direction side = context.getClickedFace();
        Vec3 clickLocation = context.getClickLocation().subtract(pos.getCenter());

        if (effect(player, level.getBlockState(pos), level, pos, side, clickLocation)) {
            if (!level.isClientSide) {
                ManaItemHandler.instance().requestManaExactForTool(rod, player, COST, true);
                var particle = SparkleParticleData.sparkle(1F, 1f, 1f, 1f, 5);
                for (int i = 0; i < 6; i++) {
                    level.addParticle(particle, pos.getX() + side.getStepX() + Math.random(), pos.getY() + side.getStepY() + Math.random(), pos.getZ() + side.getStepZ() + Math.random(), 0, 0, 0);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.FAIL;
    }

    protected boolean effect(Player player, BlockState targetState, LevelAccessor level, BlockPos pos, Direction side, Vec3 clickLocation) {
        Block block = targetState.getBlock();
        var stateDef = block.getStateDefinition();
        boolean cycleBackwards = player.isSecondaryUseActive();

        BlockState newState;

        // Stairs
        if (targetState.is(STAIRS_LIKE)) {
            var property = stateDef.getProperty("shape");
            if (property == null) return false;
            newState = cycleState(targetState, property, cycleBackwards);
        }
        // Fences, Bars, and Panes - we check the class because there is no standard block tag for non-glass panes
        else if (targetState.is(FENCE_LIKE) || block instanceof IronBarsBlock) {
            if (side == Direction.UP || side == Direction.DOWN) return false;
            side = getNaturalizedXZDirection(clickLocation);

            var property = stateDef.getProperty(side.getName());
            if (property == null) return false;
            newState = cycleState(targetState, property, cycleBackwards);
        }
        // Walls
        else if (targetState.is(WALL_LIKE)) {
            if (side == Direction.DOWN) return false;
            if (side != Direction.UP) side = getNaturalizedXZDirection(clickLocation);

            var property = stateDef.getProperty(side.getName());
            if (property == null) return false;
            newState = cycleState(targetState, property, cycleBackwards);

            // Prevent post being removed with all 4 sides are set none
            // There are other states with glitchy geometry, but this is the one that matters
            if (newState.getValue(BlockStateProperties.NORTH_WALL) == WallSide.NONE && newState.getValue(BlockStateProperties.EAST_WALL) == WallSide.NONE &&
                newState.getValue(BlockStateProperties.SOUTH_WALL) == WallSide.NONE && newState.getValue(BlockStateProperties.WEST_WALL) == WallSide.NONE &&
                !newState.getValue(BlockStateProperties.UP))
                    return false;
        }
        else return false;

        if (!level.isClientSide()) {
            // Try to lock the block's state for e.g. Framed Blocks
            newState = trySetBooleanBlockProperty(newState, "locked", true);

            level.setBlock(pos, newState, UPDATE_FLAGS);

            level.playSound(null, pos, newState.getSoundType().getPlaceSound(), SoundSource.BLOCKS);
        }
        return true;
    }

    protected static <T extends Comparable<T>> BlockState cycleState(BlockState state, Property<T> property, boolean cycleBackwards) {
        var nextValue = cycleProperty(property.getPossibleValues(), state.getValue(property), cycleBackwards);
        return state.setValue(property, nextValue);
    }

    protected static <T> T cycleProperty(Iterable<T> values, @Nullable T currentValue, boolean cycleBackwards) {
        return (T)(cycleBackwards ? Util.findPreviousInIterable(values, currentValue) : Util.findNextInIterable(values, currentValue));
    }

    protected static Direction getNaturalizedXZDirection(Vec3 clickLocation) {
        return (Math.abs(clickLocation.x) > Math.abs(clickLocation.z))
                ? (clickLocation.x > 0 ? Direction.EAST : Direction.WEST)
                : (clickLocation.z > 0 ? Direction.SOUTH : Direction.NORTH);
    }
}
