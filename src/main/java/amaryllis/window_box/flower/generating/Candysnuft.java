package amaryllis.window_box.flower.generating;

import amaryllis.window_box.Client;
import amaryllis.window_box.DataGen;
import amaryllis.window_box.Registry;
import amaryllis.window_box.Util;
import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.flower.FlowerHelper;
import amaryllis.window_box.flower.functional.Snapdresson;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vazkii.botania.api.block_entity.GeneratingFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.client.render.block_entity.SpecialFlowerBlockEntityRenderer;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.FloatingSpecialFlowerBlock;
import vazkii.botania.common.item.block.SpecialFlowerBlockItem;

import java.util.ArrayList;
import java.util.List;

import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.Registry.ClientOnly.RegisterBlockEntityRenderer;
import static amaryllis.window_box.flower.CustomFlower.FLOATING;
import static net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn;

public class Candysnuft extends GeneratingFlowerBlockEntity {

    public static final String ID = "candysnuft";
    public static final int MANA_PER_CANDLE = 3000;

    private static final BlockPos[] POSITIONS = Util.POSITIONS_2M_SQUARE;
    private static final Logger log = LoggerFactory.getLogger(Candysnuft.class);

    public static RegistryObject<SoundEvent> GENERATE_SOUND;
    private static ParticleOptions BREAK_PARTICLE = null;

    private static final String TAG_CANDLE_TARGETS = "candle_targets";
    private final boolean[] candleTargets = new boolean[POSITIONS.length];
    private boolean candleTargetsHaveChanged = false;

    private static final String TAG_CANDLE_STREAK = "candle_streak";
    private final List<Integer> candleStreak = new ArrayList<>(4);


    public static void register() {
        RegisterBlockOnly(ID, () -> new CustomFlower(ID, () -> (BlockEntityType<Candysnuft>) getBlockEntityType(ID)));
        RegisterItem(ID, () -> new SpecialFlowerBlockItem(getBlock(ID), new Item.Properties()));
        RegisterBlock(FLOATING(ID), () -> new FloatingSpecialFlowerBlock(BotaniaBlocks.FLOATING_PROPS, () -> (BlockEntityType<Candysnuft>) getBlockEntityType(ID)));
        FlowerHelper.RegisterPottedFlower(ID);

        CustomFlower.RegisterBlockEntityType(ID, Candysnuft::new);
        unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            RegisterBlockEntityRenderer(ID, SpecialFlowerBlockEntityRenderer<Candysnuft>::new);
            RegisterWandHUD(ID, Client.GENERATING_FLOWER_HUD);
        });

        CustomFlower.RegisterStewEffect(ID, MobEffects.FIRE_RESISTANCE, 4);

        DataGen.SpecialFlower(ID, CustomFlower.FlowerType.GENERATING);

        GENERATE_SOUND = Registry.RegisterSound(ID + "_generate");
    }

    public Candysnuft(BlockPos pos, BlockState state) {
        super(getBlockEntityType(ID), pos, state);
    }

    @Override
    public void tickFlower() {
        super.tickFlower();

        if (level.isClientSide || ticksExisted % 2 != 0 || getMana() >= getMaxMana()) return;

        boolean generatedMana = false;
        for (int i = 0; i < POSITIONS.length; i++) {
            if (handlePosition(i)) generatedMana = true;
        }
        if (candleTargetsHaveChanged) {
            candleTargetsHaveChanged = false;
            setChanged();
        }
        if (generatedMana) {
            level.playSound(null, getEffectivePos(), GENERATE_SOUND.get(), SoundSource.BLOCKS);
            sync();
        }
    }
    protected void updateCandleTarget(int index, boolean value) {
        candleTargets[index] = value;
        candleTargetsHaveChanged = true;
    }
    protected boolean handlePosition(int index) {
        BlockPos pos = getEffectivePos().offset(POSITIONS[index]);

        BlockState state = level.getBlockState(pos);
        // If not a candle, return
        if (!(state.getBlock() instanceof CandleBlock)) {
            if (candleTargets[index]) updateCandleTarget(index, false);
            return false;
        }
        // If too many candles, return
        int candles = state.getValue(CandleBlock.CANDLES);
        if (candles >= 4) {
            if (candleTargets[index]) updateCandleTarget(index, false);
            return false;
        }
        // If lit, remember state and return
        if (state.getValue(CandleBlock.LIT)) {
            if (!candleTargets[index]) updateCandleTarget(index, true);
            return false;
        }
        // Candle is simply unlit
        if (!candleTargets[index]) return false;

        // Candle has been extinguished
        updateCandleTarget(index, false);

        int mana = getManaAndUpdateStreak(index);
        if (mana > 0 && getMana() + mana < getMaxMana()) {
            // Decrement candle block and generate mana
            addMana(mana);
            level.setBlockAndUpdate(pos, (candles > 1)
                    ? state.setValue(CandleBlock.CANDLES, candles - 1)
                    : Blocks.AIR.defaultBlockState()
            );
            if (BREAK_PARTICLE == null) BREAK_PARTICLE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STRIPPED_BIRCH_WOOD.defaultBlockState());
            for (int i = 0; i < 4; i++)
                level.addParticle(BREAK_PARTICLE, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 0, 0, 0);
            level.playSound(null, pos, SoundEvents.CANDLE_BREAK, SoundSource.BLOCKS);
            return true;
        }
        return false;
    }

    protected int getManaAndUpdateStreak(int posIndex) {
        // Parse repeats from streak
        int repeats = 0;
        for (int index: candleStreak) {
            if (index == posIndex) repeats++;
        }
        // Update streak
        if (candleStreak.size() >= 4) candleStreak.remove(0);
        candleStreak.add(posIndex);

        if (repeats >= 4) return 0;

        // Calculate mana
        int mana = MANA_PER_CANDLE;
        if (repeats > 0) {
            mana = (int) (mana / Math.pow(repeats + 1, 0.6 * repeats));
        }
        return Math.max(mana, 0);
    }


    @Override
    public RadiusDescriptor getRadius() {
        return RadiusDescriptor.Rectangle.square(getEffectivePos(), 2);
    }

    @Override
    public int getColor() {
        return 0xddee66;
    }

    @Override
    public int getMaxMana() {
        return MANA_PER_CANDLE * 16;
    }

    @Override
    public void writeToPacketNBT(CompoundTag cmp) {
        super.writeToPacketNBT(cmp);

        var targets = new int[candleTargets.length];
        for (int i = 0; i < candleTargets.length; i++) targets[i] = candleTargets[i] ? 1 : 0;
        cmp.putIntArray(TAG_CANDLE_TARGETS, targets);

        var streak = new int[candleStreak.size()];
        for (int i = 0; i < candleStreak.size(); i++) streak[i] = candleStreak.get(i);
        cmp.putIntArray(TAG_CANDLE_STREAK, streak);
    }

    @Override
    public void readFromPacketNBT(CompoundTag cmp) {
        super.readFromPacketNBT(cmp);

        var targets = cmp.getIntArray(TAG_CANDLE_TARGETS);
        for (int i = 0; i < targets.length; i++) {
            candleTargets[i] = (targets[i] == 1);
        }

        candleStreak.clear();
        for (int index: cmp.getIntArray(TAG_CANDLE_STREAK)) candleStreak.add(index);
    }

}
