package amaryllis.window_box.flower.generating;

import amaryllis.window_box.*;
import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.flower.FlowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector2i;
import vazkii.botania.api.block_entity.GeneratingFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.api.block_entity.SpecialFlowerBlockEntity;
import vazkii.botania.client.render.block_entity.SpecialFlowerBlockEntityRenderer;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.FloatingSpecialFlowerBlock;
import vazkii.botania.common.item.block.SpecialFlowerBlockItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.flower.CustomFlower.FLOATING;

public class QueenAnelace extends GeneratingFlowerBlockEntity {

    public static final String ID = "queen_anelace";
    public static final int MANA_GENERATED = 2000;
    public static final int MANA_PENALTY = 2000;
    public static final int MAX_DEFICIT = MANA_PENALTY * 3;
    public static final int RANGE = 4;
    public static final int RANGE_Y = 8;

    public static final String SUSPICIOUS_GRASS_ID = "suspicious_grass";

    public static RegistryObject<SoundEvent> GENERATE_SOUND;
    public static RegistryObject<SoundEvent> FAIL_SOUND;

    public static final String TAG_PLANE_POSITIONS = "planePositions";
    private final List<BlockPos> planePositions = new ArrayList<>();

    public static final String TAG_TARGET_OFFSET = "targetOffset";
    protected Vector2i targetOffset = new Vector2i(3, -4);

    public static final String TAG_HEIGHT_BELOW = "heightBelow";
    protected int heightBelow = -1;

    public static final String TAG_HAS_PLACED_TARGET = "hasPlacedTarget";
    protected boolean hasPlacedTarget = false;

    public static final String TAG_MANA_DEFICIT = "manaDeficit";
    protected int manaDeficit = 0;

    protected boolean hasChanged = false;


    public static void register() {
        RegisterBlockOnly(ID, () -> new QueenAnelace.Block(ID, () -> (BlockEntityType<QueenAnelace>) getBlockEntityType(ID)));
        RegisterItem(ID, () -> new SpecialFlowerBlockItem(getBlock(ID), new Item.Properties()));
        RegisterBlock(FLOATING(ID), () -> new QueenAnelace.FloatingBlock(BotaniaBlocks.FLOATING_PROPS, () -> (BlockEntityType<QueenAnelace>) getBlockEntityType(ID)));
        FlowerHelper.RegisterPottedFlower(ID);

        CustomFlower.RegisterBlockEntityType(ID, QueenAnelace::new);
        RegisterBlockEntityRenderer(ID, SpecialFlowerBlockEntityRenderer<QueenAnelace>::new);
        RegisterWandHUD(ID, Client.GENERATING_FLOWER_HUD);

        CustomFlower.RegisterStewEffect(ID, MobEffects.WATER_BREATHING, 8);

        RegisterBlockOnly(SUSPICIOUS_GRASS_ID, SuspiciousGrass::new);
        // Manual blockstate for random rotation
        DataGen.TagBlockNonMovable(SUSPICIOUS_GRASS_ID);

        DataGen.SpecialFlower(ID, CustomFlower.FlowerType.GENERATING);

        GENERATE_SOUND = Registry.RegisterSound(ID + "_generate");
        FAIL_SOUND = Registry.RegisterSound(ID + "_fail");
    }

    public QueenAnelace(BlockPos pos, BlockState state) {
        super(getBlockEntityType(ID), pos, state);
    }

    @Override
    public void setChanged() {
        hasChanged = false;
        super.setChanged();
    }

    @Override
    public void tickFlower() {
        super.tickFlower();

        if (level.isClientSide && manaDeficit < 0) {
            for (int i = 0; i < 3; i++) Util.doNegativeWispParticles(this);
        }

        if (level.isClientSide || getMana() >= getMaxMana()) return;

        boolean forceRefresh = (ticksExisted % 20 == 0);
        var prevHeightBelow = heightBelow;
        getHeightBelow(forceRefresh);
        if (prevHeightBelow != heightBelow) {
            reset();
        }
        if (heightBelow == -1) return;

        // Place Suspicious Grass at target
        var SUSPICIOUS_GRASS = Registry.getBlock(SUSPICIOUS_GRASS_ID);
        if (!hasPlacedTarget) {
            if (ticksExisted % 20 == 0) {
                if (targetOffset == null) randomizeTargetPos();
                var targetPos = getTargetPos();
                var state = level.getBlockState(targetPos);
                if (state.is(Blocks.GRASS_BLOCK) && !state.getValue(GrassBlock.SNOWY)) { // Disallow converting snowy grass blocks, as the Suspicious Grass block doesn't have a snowy state
                    level.setBlockAndUpdate(targetPos, SUSPICIOUS_GRASS.defaultBlockState());
                    hasPlacedTarget = true;
                    refreshPlanePositions();
                    updateSignal();
                }
            }
            if (hasChanged) setChanged();
            return;
        }

        // Shouldn't happen naturally, but just in case
        if (targetOffset == null) {
            reset();
            setChanged();
            return;
        }

        // Verify which blocks have been broken
        var targetPos = getTargetPos();
        boolean removedTarget = false;
        int generatedMana = 0;
        int manaLoss = 0;
        for (var pos : planePositions) {
            var state = level.getBlockState(pos);
            if (state.is(SUSPICIOUS_GRASS)) continue;

            if (pos.equals(targetPos)) {
                // Target is not Suspicious Grass -> generate mana
                generatedMana += MANA_GENERATED;
                removedTarget = true;
            } else if (!state.is(Blocks.GRASS_BLOCK)) {
                // Block has been removed -> remove mana
                manaLoss += MANA_PENALTY;
            }
        }
        if (removedTarget) {
            // Reset after target has been removed
            reset();
        } else {
            // Forget removed blocks so we don't count them again
            planePositions.removeIf(pos -> {
                var state = level.getBlockState(pos);
                return !(state.is(Blocks.GRASS_BLOCK) || state.is(SUSPICIOUS_GRASS));
            });
        }

        // Update mana
        manaDeficit = Math.max(manaDeficit + generatedMana - manaLoss, -MAX_DEFICIT);
        if (manaDeficit > 0) {
            addMana(manaDeficit);
            manaDeficit = 0;
        }
        setChanged();
        sync();

        if (manaLoss > 0) {
            level.playSound(null, getEffectivePos(), FAIL_SOUND.get(), SoundSource.BLOCKS);
        } else if (generatedMana > 0) {
            level.playSound(null, getEffectivePos(), GENERATE_SOUND.get(), SoundSource.BLOCKS);
        }
    }

    public int getHeightBelow() { return getHeightBelow(false); }
    public int getHeightBelow(boolean forceRefresh) {
        if (heightBelow == -1 || forceRefresh) {
            heightBelow = -1;
            for (int y = 1; y <= RANGE_Y; y++) {
                var pos = getEffectivePos().relative(Direction.DOWN, y);
                if (level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                    heightBelow = y;
                    break;
                }
            }
        }
        return heightBelow;
    }

    protected void reset() {
        targetOffset = null;
        hasPlacedTarget = false;
        planePositions.clear();
        updateSignal();
        hasChanged = true;
    }

    protected void updateSignal() {
        level.updateNeighborsAt(getEffectivePos(), getBlock(ID));
    }

    protected void refreshPlanePositions() {
        var SUSPICIOUS_GRASS = Registry.getBlock(SUSPICIOUS_GRASS_ID);
        planePositions.clear();
        var origin = getEffectivePos();
        for (int x = -RANGE; x <= RANGE; x++) {
            for (int z = -RANGE; z <= RANGE; z++) {
                if (x == 0 && z == 0) continue;

                var pos = origin.offset(x, -heightBelow, z);
                var state = level.getBlockState(pos);
                if (state.is(Blocks.GRASS_BLOCK) || state.is(SUSPICIOUS_GRASS)) {
                    planePositions.add(pos.immutable());
                }
            }
        }
        hasChanged = true;
    }

    protected void randomizeTargetPos() {
        int x = 0, z = 0;
        while (x == 0 && z == 0) {
            x = level.random.nextIntBetweenInclusive(-RANGE, RANGE);
            z = level.random.nextIntBetweenInclusive(-RANGE, RANGE);
        }
        targetOffset = new Vector2i(x, z);
    }

    public BlockPos getTargetPos() {
        if (targetOffset == null) return null;
        return getEffectivePos().offset(targetOffset.x, -getHeightBelow(), targetOffset.y);
    }
    public BlockState getTarget() {
        if (targetOffset == null) return null;
        return level.getBlockState(getTargetPos());
    }

    public int getSignal(Direction direction) {
        var target = getTarget();
        if (target == null) return 0;
        if (!(target.is(Blocks.GRASS_BLOCK) || target.is(Registry.getBlock(SUSPICIOUS_GRASS_ID)))) return 0;

        return switch (direction) {
            case EAST -> (targetOffset.x > 0) ? targetOffset.x : 0;
            case WEST -> (targetOffset.x < 0) ? -targetOffset.x : 0;
            case SOUTH -> (targetOffset.y > 0) ? targetOffset.y : 0;
            case NORTH -> (targetOffset.y < 0) ? -targetOffset.y : 0;
            default -> 0;
        };
    }


    @Override
    public RadiusDescriptor getRadius() {
        return RadiusDescriptor.Rectangle.square(getEffectivePos(), RANGE);
    }

    @Override
    public int getColor() {
        return 0xe68b2d;
    }

    @Override
    public int getMaxMana() {
        return MANA_GENERATED;
    }


    @Override
    public void writeToPacketNBT(CompoundTag tag) {
        super.writeToPacketNBT(tag);

        if (!planePositions.isEmpty()) {
            tag.putIntArray(TAG_PLANE_POSITIONS, Util.SerializeBlockPositions(planePositions));
        }
        if (targetOffset != null) {
            tag.putInt(TAG_TARGET_OFFSET + "X", targetOffset.x);
            tag.putInt(TAG_TARGET_OFFSET + "Z", targetOffset.y);
        }
        if (heightBelow != -1) tag.putInt(TAG_HEIGHT_BELOW, heightBelow);
        tag.putBoolean(TAG_HAS_PLACED_TARGET, hasPlacedTarget);
        tag.putInt(TAG_MANA_DEFICIT, manaDeficit);
    }

    @Override
    public void readFromPacketNBT(CompoundTag tag) {
        super.readFromPacketNBT(tag);

        planePositions.clear();
        if (tag.contains(TAG_PLANE_POSITIONS)) {
            Util.DeserializeBlockPositions(tag.getIntArray(TAG_PLANE_POSITIONS), planePositions);
        }
        targetOffset = (tag.contains(TAG_TARGET_OFFSET + "X") && tag.contains(TAG_TARGET_OFFSET + "Z"))
            ? new Vector2i(tag.getInt(TAG_TARGET_OFFSET + "X"), tag.getInt(TAG_TARGET_OFFSET + "Z"))
            : null;
        heightBelow = tag.contains(TAG_HEIGHT_BELOW) ? tag.getInt(TAG_HEIGHT_BELOW) : -1;
        hasPlacedTarget = tag.contains(TAG_HAS_PLACED_TARGET) ? tag.getBoolean(TAG_HAS_PLACED_TARGET) : false;
        manaDeficit = tag.contains(TAG_MANA_DEFICIT) ? tag.getInt(TAG_MANA_DEFICIT) : 0;
    }


    protected static class Block extends CustomFlower {
        public Block(String ID, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType) {
            super(ID, blockEntityType);
        }

        @Override
        public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction oppositeDir) {
            return (level.getBlockEntity(pos) instanceof QueenAnelace flower) ? flower.getSignal(oppositeDir.getOpposite()) : 0;
        }
    }

    protected static class FloatingBlock extends FloatingSpecialFlowerBlock {
        public FloatingBlock(Properties properties, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType) {
            super(properties, blockEntityType);
        }

        @Override
        public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction oppositeDir) {
            return (level.getBlockEntity(pos) instanceof QueenAnelace flower) ? flower.getSignal(oppositeDir.getOpposite()) : 0;
        }
    }

    protected static class SuspiciousGrass extends net.minecraft.world.level.block.Block {
        public SuspiciousGrass() {
            super(propOf(Blocks.GRASS_BLOCK).pushReaction(PushReaction.DESTROY));
        }

        public String getDescriptionId() {
            return "block.minecraft.grass_block";
        }
    }

}
