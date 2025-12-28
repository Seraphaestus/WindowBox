package amaryllis.window_box.flower.functional;

import amaryllis.window_box.*;
import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.flower.FlowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.client.render.block_entity.SpecialFlowerBlockEntityRenderer;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.FloatingSpecialFlowerBlock;
import vazkii.botania.common.item.block.SpecialFlowerBlockItem;

import java.util.HashMap;
import java.util.Map;

import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.Registry.ClientOnly.RegisterBlockEntityRenderer;
import static amaryllis.window_box.flower.CustomFlower.FLOATING;
import static net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn;

@Mod.EventBusSubscriber(modid = WindowBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GloryIncarnata extends FunctionalFlowerBlockEntity {

    public static final String ID = "glory_incarnata";

    protected static final String PROTECTING = "protecting";
    protected static final String CURING = "curing";

    public static final int PASSIVE_COST = 1;
    public static final int ACTIVE_COST = 10000;
    public static final int RANGE = 21;
    public static final int RANGE_Y = 9;

    public static final int REFRESH_DURATION = 3 * Util.SECONDS;

    public static Map<EntityType<? extends Mob>, Tuple<EntityType<? extends Mob>, Integer>> CURABLE = new HashMap<>();

    public static void register() {
        RegisterBlockOnly(ID, () -> new CustomFlower(ID, () -> (BlockEntityType<GloryIncarnata>) getBlockEntityType(ID)));
        RegisterItem(ID, () -> new SpecialFlowerBlockItem(getBlock(ID), new Item.Properties()));
        RegisterBlock(FLOATING(ID), () -> new FloatingSpecialFlowerBlock(BotaniaBlocks.FLOATING_PROPS, () -> (BlockEntityType<GloryIncarnata>) getBlockEntityType(ID)));
        FlowerHelper.RegisterPottedFlower(ID);

        CustomFlower.RegisterBlockEntityType(ID, GloryIncarnata::new);
        unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            RegisterBlockEntityRenderer(ID, SpecialFlowerBlockEntityRenderer<GloryIncarnata>::new);
            RegisterWandHUD(ID, Client.FUNCTIONAL_FLOWER_HUD);
        });

        CustomFlower.RegisterStewEffect(ID, MobEffects.SATURATION, 7);

        DataGen.SpecialFlower(ID, CustomFlower.FlowerType.FUNCTIONAL);
    }

    public GloryIncarnata(BlockPos pos, BlockState state) {
        super(getBlockEntityType(ID), pos, state);
    }

    public static void onConfigFirstLoaded() {
        CURABLE.clear();
        var curableConfig = Config.GLORY_INCARNATA_CURABLE.get();
        int entries = curableConfig.size() / 3;
        for (int i = 0; i < entries * 3; i += 3) {
            var originalID = ResourceLocation.parse(curableConfig.get(i));
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(originalID)) continue;

            var newID = ResourceLocation.parse(curableConfig.get(i + 1));
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(newID)) continue;

            var timeStr = curableConfig.get(i + 2);
            if (!timeStr.matches("\\d+")) continue;
            int time = Integer.parseInt(timeStr);

            try {
                var originalEntity = (EntityType<? extends Mob>) ForgeRegistries.ENTITY_TYPES.getValue(originalID);
                try {
                    var newEntity = (EntityType<? extends Mob>) ForgeRegistries.ENTITY_TYPES.getValue(newID);
                    CURABLE.put(originalEntity, new Tuple<>(newEntity, time));
                } catch(ClassCastException e) {
                    WindowBox.LOGGER.error("Could not register Glory Incarnata conversion: {} is not a valid Mob", newID);
                }
            } catch(ClassCastException e) {
                WindowBox.LOGGER.error("Could not register Glory Incarnata conversion: {} is not a valid Mob", originalID);
            }
        }
    }

    @SubscribeEvent
    public static void updateEntities(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        serverLevel.getEntities().getAll().forEach(entity -> {
            if (!(entity instanceof Mob mob)) return;
            var allData = mob.getPersistentData();
            if (!allData.contains(ID)) return;
            var data = allData.getCompound(ID);
            if (data.contains(PROTECTING) && !Util.ModifyData_PositiveInt(allData, ID, data, PROTECTING, -1)) {
                setZombifyingImmunity(mob, false);
            }
            if (data.contains(CURING)) {
                if (Util.ModifyData_PositiveInt(allData, ID, data, CURING, -1)) {
                    updateConvertingEntity(mob);
                } else {
                    convertEntity(serverLevel, mob, CURABLE.get(mob.getType()).getA());
                }
            }
        });
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        if (level.isClientSide || getMana() < PASSIVE_COST) return;

        boolean applyingPassiveEffect = false;
        boolean usedMana = false;

        for (Mob entity: level.getEntitiesOfClass(Mob.class, new AABB(getEffectivePos().offset(-RANGE, -RANGE_Y, -RANGE), getEffectivePos().offset(RANGE + 1, RANGE_Y + 1, RANGE + 1)))) {
            var data = entity.getPersistentData();

            // Prevent Piglins and Hoglins from zombifying
            if (!level.dimensionType().piglinSafe() && canZombify(entity)) {
                setZombifyingImmunity(entity, true);
                Util.SetData_Int(data, ID, PROTECTING, REFRESH_DURATION);
                applyingPassiveEffect = true;
            }

            // Cure entities (Zombie Villagers, Zombified Piglins, and Zoglins)
            if (getMana() >= ACTIVE_COST && CURABLE.containsKey(entity.getType()) && !Util.HasData(data, ID, CURING)) {
                Util.SetData_Int(data, ID, CURING, CURABLE.get(entity.getType()).getB());
                addMana(-ACTIVE_COST);
                usedMana = true;
            }
        }
        if (applyingPassiveEffect) {
            if (level.random.nextInt(8) == 0) Util.doActiveWispParticles(this, getColor());
            addMana(-PASSIVE_COST);
            usedMana = true;
        }
        if (usedMana) sync();
    }

    protected static boolean canZombify(Entity entity) {
        return entity instanceof AbstractPiglin || entity instanceof Hoglin;
    }
    protected static boolean isZombifying(Entity entity) {
        if (entity instanceof AbstractPiglin piglin) return piglin.isConverting();
        if (entity instanceof Hoglin hoglin) return hoglin.isConverting();
        return false;
    }
    protected static void setZombifyingImmunity(Entity entity, boolean value) {
        if (entity instanceof AbstractPiglin piglin) piglin.setImmuneToZombification(value);
        else if (entity instanceof Hoglin hoglin) hoglin.setImmuneToZombification(value);
    }

    protected static void updateConvertingEntity(Mob entity) {
        var random = entity.getRandom();
        double shiverX = 0.05 * (random.nextDouble() - 0.5);
        double shiverZ = 0.05 * (random.nextDouble() - 0.5);
        entity.setPos(entity.position().add(shiverX, 0, shiverZ));
    }
    protected static <T extends Mob> void convertEntity(ServerLevel level, Mob originalEntity, EntityType<T> entityType) {
        T newEntity = originalEntity.convertTo(entityType, false);

        if (originalEntity instanceof ZombieVillager zombieVillager && newEntity instanceof Villager villager) {
            var entityData = zombieVillager.serializeNBT();
            var armorDropChances = entityData.getList("ArmorDropChances", Tag.TAG_LIST);
            var handDropChances = entityData.getList("HandDropChances", Tag.TAG_LIST);
            for(EquipmentSlot slot: EquipmentSlot.values()) {
                ItemStack stack = zombieVillager.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                if (EnchantmentHelper.hasBindingCurse(stack)) {
                    villager.getSlot(slot.getIndex() + 300).set(stack);
                } else {
                    double dropChance = (slot.isArmor() ? armorDropChances : handDropChances).getFloat(slot.getIndex());
                    if (dropChance > 1.0D) zombieVillager.spawnAtLocation(stack);
                }
            }
            villager.setVillagerData(zombieVillager.getVillagerData());
            if (entityData.contains("Gossips")) villager.setGossips(entityData.getCompound("Gossips"));
            if (entityData.contains("Offers")) villager.setOffers(new MerchantOffers(entityData.getCompound("Offers")));
            if (entityData.contains("Xp")) villager.setVillagerXp(entityData.getInt("Xp"));
            villager.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()), MobSpawnType.CONVERSION, null, null);
            villager.refreshBrain(level);
        }

        newEntity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        if (!originalEntity.isSilent()) {
            level.levelEvent(null, 1027, originalEntity.blockPosition(), 0);
        }
        net.minecraftforge.event.ForgeEventFactory.onLivingConvert(originalEntity, newEntity);
    }

    @Override
    public RadiusDescriptor getRadius() {
        return RadiusDescriptor.Rectangle.square(getEffectivePos(), RANGE);
    }

    @Override
    public int getColor() {
        return 0xff9933;
    }

    @Override
    public int getMaxMana() {
        return ACTIVE_COST;
    }

}
