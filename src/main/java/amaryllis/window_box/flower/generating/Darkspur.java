package amaryllis.window_box.flower.generating;

import amaryllis.window_box.Client;
import amaryllis.window_box.DataGen;
import amaryllis.window_box.Util;
import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.flower.FlowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.ForgeRegistries;
import vazkii.botania.api.block_entity.GeneratingFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.client.render.block_entity.SpecialFlowerBlockEntityRenderer;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.FloatingSpecialFlowerBlock;
import vazkii.botania.common.item.block.SpecialFlowerBlockItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.flower.CustomFlower.FLOATING;
import static net.minecraft.world.item.HoneycombItem.WAXABLES;

public class Darkspur extends GeneratingFlowerBlockEntity {

    public static final String ID = "darkspur";
    public static final int MANA_PER_BULB = 200;
    public static final int INTERVAL = 5 * Util.SECONDS;
    public static final int RANGE = 3;
    public static final int RANGE_Y = 1;

    public static final String TAG_COPPER_BULB_TARGETS = "copper_bulb_targets";
    private final List<BlockPos> copperBulbTargets = new ArrayList<>();

    public static void register() {
        RegisterBlockOnly(ID, () -> new CustomFlower(ID, () -> (BlockEntityType<Darkspur>) getBlockEntityType(ID)));
        RegisterItem(ID, () -> new SpecialFlowerBlockItem(getBlock(ID), new Item.Properties()));
        RegisterBlock(FLOATING(ID), () -> new FloatingSpecialFlowerBlock(BotaniaBlocks.FLOATING_PROPS, () -> (BlockEntityType<Darkspur>) getBlockEntityType(ID)));
        FlowerHelper.RegisterPottedFlower(ID);

        CustomFlower.RegisterBlockEntityType(ID, Darkspur::new);
        RegisterBlockEntityRenderer(ID, SpecialFlowerBlockEntityRenderer<Darkspur>::new);
        RegisterWandHUD(ID, Client.GENERATING_FLOWER_HUD);

        CustomFlower.RegisterStewEffect(ID, MobEffects.DARKNESS, 3);

        DataGen.markOptional(ID, FLOATING(ID));
        DataGen.SpecialFlower(ID, CustomFlower.FlowerType.GENERATING);
    }

    public Darkspur(BlockPos pos, BlockState state) {
        super(getBlockEntityType(ID), pos, state);
    }

    @Override
    public void tickFlower() {
        super.tickFlower();

        if (level.isClientSide) return;

        if (getMana() >= getMaxMana()) {
            resetTargets();
            return;
        }

        int time = ticksExisted % INTERVAL;
        if (time == 0) {
            // Recalibrate: if the copper bulb targets have changed, restart timer, else refresh bulbs
            List<BlockPos> newTargets = new ArrayList<>();
            boolean hasChanged = false;
            for (var pos: getTargetPositions()) {
                var state = level.getBlockState(pos);
                boolean expectedTarget = copperBulbTargets.contains(pos);
                if (isCopperBulb(state)) {
                    newTargets.add(pos.immutable());
                    if (!expectedTarget) hasChanged = true;
                } else if (expectedTarget) hasChanged = true;
            }
            if (hasChanged) {
                // Reset timer by clearing targets, or restart timer if targets have not changed since timer was reset
                if (copperBulbTargets.isEmpty()) copperBulbTargets.addAll(newTargets);
                else resetTargets();
                return;
            }
            // Randomize bulbs
            for (var pos: copperBulbTargets) {
                var state = level.getBlockState(pos);
                var newState = state.setValue(BlockStateProperties.LIT, Math.random() > 0.5);
                if (!state.equals(newState)) level.setBlockAndUpdate(pos, newState);
            }
            return;
        }

        if (copperBulbTargets.isEmpty()) return;

        // Check if targets are all lit
        for (var pos: copperBulbTargets) {
            var state = level.getBlockState(pos);
            if (!isCopperBulb(state)) {
                // Targets has been invalidated
                resetTargets();
                return;
            }
            if (!state.getValue(BlockStateProperties.LIT)) {
                // Target bulb is unlit, so exit early
                return;
            }
        }
        // Targets are all lit, so generate mana and deluminate bulbs
        for (var pos: copperBulbTargets) {
            var state = level.getBlockState(pos).setValue(BlockStateProperties.LIT, false);
            state = weatherCopperBlock(state);
            level.setBlockAndUpdate(pos, state);
        }
        addMana(MANA_PER_BULB * copperBulbTargets.size());
        sync();
        copperBulbTargets.clear();
    }

    protected void resetTargets() {
        if (copperBulbTargets.isEmpty()) return;
        for (var pos: copperBulbTargets) {
            var state = level.getBlockState(pos);
            if (isCopperBulb(state) && state.getValue(BlockStateProperties.LIT)) {
                level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, false));
            }
        }
        copperBulbTargets.clear();
    }

    protected Iterable<BlockPos> getTargetPositions() {
        var pos = getEffectivePos();
        return BlockPos.betweenClosed(
                pos.getX() - RANGE, pos.getY() - RANGE_Y, pos.getZ() - RANGE,
                pos.getX() + RANGE, pos.getY() + RANGE_Y, pos.getZ() + RANGE);
    }

    protected boolean isCopperBulb(BlockState state) {
        ResourceLocation ID = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return ID != null && ID.getPath().endsWith("copper_bulb") && state.hasProperty(BlockStateProperties.LIT);
    }

    protected static BlockState weatherCopperBlock(BlockState state) {
        // Remove wax
        var unwaxedBlock = WAXABLES.get().inverse().get(state.getBlock());
        if (unwaxedBlock != null) {
            state =  unwaxedBlock.withPropertiesOf(state);
        }
        // Weather
        if (state.getBlock() instanceof WeatheringCopper copperBlock) {
            var stagesToOxidize = Math.random();
            // 25% chance no change, 50% chance -1 stage, 25% chance -2 stages
            if (stagesToOxidize > 0.25) {
                Optional<BlockState> nextState = copperBlock.getNext(state);
                if (nextState.isPresent() && stagesToOxidize > 0.75) {
                    nextState = copperBlock.getNext(nextState.get());
                }
                return nextState.isPresent() ? nextState.get() : Blocks.AIR.defaultBlockState();
            }
        } else {
            // Flat chance to just disappear
            if (Math.random() < 0.25) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return state;
    }


    @Override
    public RadiusDescriptor getRadius() {
        return RadiusDescriptor.Rectangle.square(getEffectivePos(), RANGE);
    }

    @Override
    public int getColor() {
        return 0x9955ff;
    }

    @Override
    public int getMaxMana() {
        return 100000;
    }


    @Override
    public void writeToPacketNBT(CompoundTag tag) {
        super.writeToPacketNBT(tag);

        if (!copperBulbTargets.isEmpty()) {
            tag.putIntArray(TAG_COPPER_BULB_TARGETS, Util.SerializeBlockPositions(copperBulbTargets));
        }
    }

    @Override
    public void readFromPacketNBT(CompoundTag tag) {
        super.readFromPacketNBT(tag);

        copperBulbTargets.clear();
        if (tag.contains(TAG_COPPER_BULB_TARGETS)) {
            Util.DeserializeBlockPositions(tag.getIntArray(TAG_COPPER_BULB_TARGETS), copperBulbTargets);
        }
    }

}
