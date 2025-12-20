package amaryllis.window_box.tree;

import amaryllis.window_box.WindowBox;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import vazkii.botania.xplat.BotaniaConfig;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public abstract class SpecialSapling extends SaplingBlock {

    protected boolean doubleWide;
    protected int freeHeight;

    public SpecialSapling(boolean doubleWide, int freeHeight) {
        super(null, BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING));
        this.doubleWide = doubleWide;
        this.freeHeight = freeHeight;
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), 4);
            return;
        }
        var growPos = getGrowPos(level, pos, state);
        if (growPos != null && saplingsHaveRoom(level, growPos)) growTree(level, growPos, random);
    }

    protected BlockPos getGrowPos(ServerLevel level, BlockPos pos, BlockState state) {
        if (!doubleWide) return pos;

        var type = state.getBlock();
        BlockPos[] origins = {pos, pos.west(), pos.north(), pos.offset(-1, 0, -1)};
        for (var origin: origins) {
            if (isTwoByTwoSapling(level, origin, type)) return origin;
        }
        return null;
    }

    protected static boolean rotatedTowardsX(Rotation rotation) { return rotation == Rotation.NONE || rotation == Rotation.CLOCKWISE_180; }

    public boolean isTwoByTwoSapling(ServerLevel level, BlockPos pos, Block type) {
        return level.getBlockState(pos).is(type) &&
               level.getBlockState(pos.east()).is(type) &&
               level.getBlockState(pos.south()).is(type) &&
               level.getBlockState(pos.offset(1, 0, 1)).is(type);
    }
    public boolean saplingsHaveRoom(ServerLevel level, BlockPos pos) {
        if (!doubleWide) {
            for (int y = 1; y <= freeHeight; y++) {
                var state = level.getBlockState(pos.relative(Direction.UP, y));
                if (!state.canBeReplaced()) return false;
            }
        } else {
            BlockPos[] origins = {pos, pos.east(), pos.south(), pos.offset(1, 0, 1)};
            for (var origin: origins) {
                for (int y = 1; y <= freeHeight; y++) {
                    var state = level.getBlockState(origin.relative(Direction.UP, y));
                    if (!state.canBeReplaced()) return false;
                }
            }
        }
        return true;
    }

    public abstract void growTree(ServerLevel level, BlockPos pos, RandomSource random);

    protected boolean placeStructure(String ID, ServerLevel level, BlockPos origin, Rotation rotation, RandomSource random) {
        Optional<StructureTemplate> structure = level.getStructureManager().get(WindowBox.RL(ID));
        if (structure.isEmpty()) {
            WindowBox.LOGGER.error("Structure not found at {}", WindowBox.RL(ID));
            return false;
        }

        var minPos = new ChunkPos(origin);
        var maxPos = new ChunkPos(origin.offset(structure.get().getSize()));
        if (ChunkPos.rangeClosed(minPos, maxPos).anyMatch(chunkPos -> !level.isLoaded(chunkPos.getWorldPosition()))) {
            WindowBox.LOGGER.error("Can't place structure {} in unloaded chunks", WindowBox.RL(ID));
            return false;
        }

        var placeSettings = (new StructurePlaceSettings()).setRotation(rotation).addProcessor(GrowProcessor.INSTANCE);
        structure.get().placeInWorld(level, origin, origin, placeSettings, random, Block.UPDATE_CLIENTS);
        return true;
    }

    protected void placePortal(ServerLevel level, BlockPos origin, Direction rotationPosDir, int size, int height, Block portal, EnumProperty<Direction.Axis> AXIS) {
        var portalState = portal.defaultBlockState().setValue(AXIS, rotationPosDir.getAxis());

        for (int y = 1; y <= height; y++) {
            var pos = origin.relative(Direction.UP, y);
            level.setBlock(pos, portalState, Block.UPDATE_CLIENTS);
            for (int i = 1; i <= size; i++) {
                level.setBlock(pos.relative(rotationPosDir, 1), portalState, Block.UPDATE_CLIENTS);
            }
        }
    }

    protected Player getNearestPlayer(ServerLevel level, Vec3 center) {
        return level.getNearestPlayer(center.x, center.y, center.z, 16, EntitySelector.NO_SPECTATORS);
    }

    protected Rotation rotateTowardsNearestPlayer(ServerLevel level, Vec3 center) {
        return rotateTowardsPlayer(level, center, getNearestPlayer(level, center));
    }
    protected Rotation rotateTowardsPlayer(ServerLevel level, Vec3 center, Player nearestPlayer) {
        if (nearestPlayer == null) return Rotation.NONE;

        var deltaX = nearestPlayer.position().x - center.x;
        var deltaZ = nearestPlayer.position().z - center.z;
        return (Math.abs(deltaX) > Math.abs(deltaZ))
                ? (deltaX > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90)
                : (deltaZ > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE);
    }

    public static class GrowProcessor extends StructureProcessor {
        public static StructureProcessorType<GrowProcessor> TYPE;
        public static TagKey<Block> REPLACEABLE = TagKey.create(Registries.BLOCK, (WindowBox.RL("replaceable_by_trees")));

        public static final GrowProcessor INSTANCE = new GrowProcessor();
        public static final Codec<GrowProcessor> CODEC = Codec.unit(() -> INSTANCE);

        @Nullable
        public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos, StructureTemplate.StructureBlockInfo blockInfo, StructureTemplate.StructureBlockInfo relativeBlockInfo, StructurePlaceSettings placeSettings) {
            var state = level.getBlockState(relativeBlockInfo.pos());
            return state.is(REPLACEABLE) ? relativeBlockInfo : null;
        }

        protected StructureProcessorType<?> getType() {
            return TYPE;
        }
    }

    public static class Item extends BlockItem {
        public Item(Block block) {
            super(block, new net.minecraft.world.item.Item.Properties());
        }

        @Override
        public void appendHoverText(@NotNull ItemStack stack, Level world, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
            if (BotaniaConfig.client() == null) return;
            if (world != null) {
                tooltip.add(Component.translatable("windowbox.flowerType.sapling").withStyle(ChatFormatting.ITALIC, ChatFormatting.BLUE));
            }

            if (BotaniaConfig.client().referencesEnabled()) {
                String key = getDescriptionId() + ".reference";
                MutableComponent lore = Component.translatable(key);
                if (!lore.getString().equals(key)) {
                    tooltip.add(lore.withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
                }
            }
        }
    }
}