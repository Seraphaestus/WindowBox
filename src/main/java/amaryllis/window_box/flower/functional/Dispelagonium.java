package amaryllis.window_box.flower.functional;

import amaryllis.window_box.Client;
import amaryllis.window_box.Config;
import amaryllis.window_box.DataGen;
import amaryllis.window_box.entity.LivingMarker;
import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.flower.FlowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.registries.ForgeRegistries;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.api.block_entity.SpecialFlowerBlockEntity;
import vazkii.botania.client.render.block_entity.SpecialFlowerBlockEntityRenderer;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.FloatingSpecialFlowerBlock;
import vazkii.botania.common.item.block.SpecialFlowerBlockItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.Registry.ClientOnly.RegisterBlockEntityRenderer;
import static amaryllis.window_box.flower.CustomFlower.FLOATING;
import static net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn;

public class Dispelagonium extends FunctionalFlowerBlockEntity {

    public static final String ID = "dispelagonium";

    public static final int RANGE = 6;
    public static final int COST = 100;

    public static Map<MobEffect, Boolean> EFFECT_BLACKLIST = new HashMap<>();

    LivingMarker effectReciever;


    public static void register() {
        RegisterBlockOnly(ID, () -> new Dispelagonium.Block(ID, () -> (BlockEntityType<Dispelagonium>) getBlockEntityType(ID)));
        RegisterItem(ID, () -> new SpecialFlowerBlockItem(getBlock(ID), new Item.Properties()));
        RegisterBlock(FLOATING(ID), () -> new Dispelagonium.FloatingBlock(BotaniaBlocks.FLOATING_PROPS, () -> (BlockEntityType<Dispelagonium>) getBlockEntityType(ID)));
        FlowerHelper.RegisterPottedFlower(ID);

        CustomFlower.RegisterBlockEntityType(ID, Dispelagonium::new);
        unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            RegisterBlockEntityRenderer(ID, SpecialFlowerBlockEntityRenderer<Dispelagonium>::new);
            RegisterWandHUD(ID, Client.FUNCTIONAL_FLOWER_HUD);
        });

        CustomFlower.RegisterStewEffect(ID, MobEffects.INVISIBILITY, 5);

        DataGen.SpecialFlower(ID, CustomFlower.FlowerType.FUNCTIONAL);
    }

    public Dispelagonium(BlockPos pos, BlockState state) {
        super(getBlockEntityType(ID), pos, state);
    }

    public static void onConfigFirstLoaded() {
        EFFECT_BLACKLIST.clear();
        for (String ID: Config.DISPELAGONIUM_BLACKLIST.get()) {
            var loc = ResourceLocation.parse(ID);
            if (!ForgeRegistries.MOB_EFFECTS.containsKey(loc)) continue;
            EFFECT_BLACKLIST.put(ForgeRegistries.MOB_EFFECTS.getValue(loc), true);
        }
    }


    public void onPlace() {
        effectReciever = LivingMarker.getOrCreateAtFlower(level, getEffectivePos(), ID);
    }
    public void onRemove() {
        if (effectReciever != null) {
            effectReciever.kill();
            effectReciever = null;
        }
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        if (level.isClientSide) return;

        if (effectReciever == null) {
            effectReciever = LivingMarker.getOrCreateAtFlower(level, getEffectivePos(), ID);
            return;
        }

        if (getMana() < COST) return;

        List<MobEffect> effects = effectReciever.getActiveEffects().stream()
                .filter(effect -> !effect.isAmbient())
                .map(MobEffectInstance::getEffect).toList();
        if (effects.isEmpty()) return;

        boolean doneWork = false;
        List<LivingEntity> entities = getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(getEffectivePos().offset(-RANGE, -RANGE, -RANGE), getEffectivePos().offset(RANGE + 1, RANGE + 1, RANGE + 1)));
        for (LivingEntity entity: entities) {
            if (entity instanceof LivingMarker) continue; // So that the Dispelagonium's potion receiver isn't counted

            for (MobEffect effect: effects) {
                if (canRemoveEffect(entity, effect)) {
                    // Remove effect
                    doneWork = true;
                    entity.removeEffect(effect);
                    addMana(-COST);
                    // Early exit
                    if (getMana() < COST) {
                        sync();
                        return;
                    }
                }
            }
        }
        if (doneWork) sync();
    }

    protected static boolean canRemoveEffect(LivingEntity entity, MobEffect effectType) {
        return entity.hasEffect(effectType);
        // NB: No need to check EFFECTS_BLACKLIST as this is applied to the LivingMarker not being able to get them in the first place
    }

    @Override
    public RadiusDescriptor getRadius() {
        return RadiusDescriptor.Rectangle.square(getEffectivePos(), RANGE);
    }
    @Override
    public RadiusDescriptor getSecondaryRadius() {
        return new RadiusDescriptor.Circle(getEffectivePos(), 4);
    }

    @Override
    public int getColor() {
        return 0xdd88ff;
    }

    @Override
    public int getMaxMana() {
        return 1000;
    }



    // Custom Block and FloatingBlock class to handle creating/destroying the marker entity
    protected static class Block extends CustomFlower {
        public Block(String ID, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType) {
            super(ID, blockEntityType);
        }

        @Override
        public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMovedByPiston) {
            super.onPlace(state, level, pos, oldState, isMovedByPiston);
            if (level.getBlockEntity(pos) instanceof Dispelagonium flower) {
                flower.onPlace();
            }
        }

        @Override
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMovedByPiston) {
            if (level.getBlockEntity(pos) instanceof Dispelagonium flower) {
                flower.onRemove();
            }
            super.onRemove(state, level, pos, newState, isMovedByPiston);
        }
    }

    protected static class FloatingBlock extends FloatingSpecialFlowerBlock {
        public FloatingBlock(Properties properties, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType) {
            super(properties, blockEntityType);
        }

        @Override
        public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMovedByPiston) {
            super.onPlace(state, level, pos, oldState, isMovedByPiston);
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof Dispelagonium flower) {
                flower.onPlace();
            }
        }

        @Override
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMovedByPiston) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof Dispelagonium flower) {
                flower.onRemove();
            }
            super.onRemove(state, level, pos, newState, isMovedByPiston);
        }
    }

}
