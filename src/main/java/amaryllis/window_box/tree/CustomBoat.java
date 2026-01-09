package amaryllis.window_box.tree;

import amaryllis.window_box.DataGen;
import amaryllis.window_box.Registry;
import amaryllis.window_box.WindowBox;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.ChestBoatModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.RegistryObject;
import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class CustomBoat extends Boat {

    public static final String ID = "boat";
    public static RegistryObject<EntityType<CustomBoat>> ENTITY_TYPE;

    protected static final EntityDataAccessor<String> DATA_VARIANT = SynchedEntityData.defineId(CustomBoat.class, EntityDataSerializers.STRING);
    protected static final String DEFAULT_VARIANT = ChthonicYew.ID;
    public static final List<String> ALL_VARIANTS = new ArrayList<>();

    public CustomBoat(EntityType<CustomBoat> entityType, Level level) {
        super(entityType, level);
    }

    public CustomBoat(Level level, double x, double y, double z) {
        super(ENTITY_TYPE.get(), level);
        setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    public static void register() {
        ENTITY_TYPE = Registry.ENTITY_TYPES.register(ID,
                () -> EntityType.Builder.<CustomBoat>of(CustomBoat::new, MobCategory.MISC)
                        .sized(1.375f, 0.5625f)
                        .build(ID)
        );
        CustomBoat.WithChest.ENTITY_TYPE = Registry.ENTITY_TYPES.register(CustomBoat.WithChest.ID,
                () -> EntityType.Builder.<CustomBoat.WithChest>of(CustomBoat.WithChest::new, MobCategory.MISC)
                        .sized(1.375f, 0.5625f)
                        .build(ID)
        );
    }
    public static void RegisterVariant(String ID) {
        ALL_VARIANTS.add(ID);
        Registry.RegisterItem(ID + "_boat", () -> new CustomBoat.BoatItem(false, ID, new Item.Properties().stacksTo(1)));
        Registry.RegisterItem(ID + "_chest_boat", () -> new CustomBoat.BoatItem(true, ID, new Item.Properties().stacksTo(1)));
    }

    @Override
    public Item getDropItem() {
        return Registry.getItem(getCustomVariant() + "_boat");
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        lastYd = this.getDeltaMovement().y;
        if (isPassenger()) return;
        if (onGround) {
            if (fallDistance > 3.0F) handleFallDamage();
            resetFallDistance();
        } else if (!canBoatInFluid(level().getFluidState(blockPosition().below())) && y < 0.0D) {
            fallDistance -= (float) y;
        }
    }
    protected void handleFallDamage() {
        if (status != Boat.Status.ON_LAND) {
            resetFallDistance();
            return;
        }

        causeFallDamage(fallDistance, 1, damageSources().fall());
        if (level().isClientSide || isRemoved()) return;

        kill();
        if (level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            for (int i = 0; i < 3; ++i) spawnAtLocation(getPlanks());
            for (int j = 0; j < 2; ++j) spawnAtLocation(Items.STICK);
        }
    }


    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_VARIANT, DEFAULT_VARIANT);
    }

    public void setVariant(String ID) {
        entityData.set(DATA_VARIANT, (ID != null && !ID.isEmpty()) ? ID : DEFAULT_VARIANT);
    }
    public String getCustomVariant() {
        return entityData.get(DATA_VARIANT);
    }
    public Block getPlanks() {
        return Registry.getBlock(getCustomVariant() + "_planks");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Type", getCustomVariant());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Type", CompoundTag.TAG_STRING)) {
            setVariant(tag.getString("Type"));
        }
    }

    public static class BoatItem extends Item {
        private static final Predicate<Entity> ENTITY_PREDICATE;
        static { ENTITY_PREDICATE = EntitySelector.NO_SPECTATORS.and(Entity::isPickable); }

        private final String variant;
        private final boolean hasChest;

        public BoatItem(boolean hasChest, String variant, net.minecraft.world.item.Item.Properties properties) {
            super(properties);
            this.hasChest = hasChest;
            this.variant = variant;
        }

        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack heldItem = player.getItemInHand(hand);
            HitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
            if (hitResult.getType() == HitResult.Type.MISS) return InteractionResultHolder.pass(heldItem);

            Vec3 view = player.getViewVector(1);
            List<Entity> entitiesInView = level.getEntities(player, player.getBoundingBox().expandTowards(view.scale(5)).inflate(1), ENTITY_PREDICATE);
            if (!entitiesInView.isEmpty()) {
                Vec3 eyePos = player.getEyePosition();
                for(Entity entity: entitiesInView) {
                    AABB bounds = entity.getBoundingBox().inflate(entity.getPickRadius());
                    if (bounds.contains(eyePos)) return InteractionResultHolder.pass(heldItem);
                }
            }

            if (hitResult.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(heldItem);
            Boat boat = getBoat(level, hitResult);
            boat.setYRot(player.getYRot());
            if (!level.noCollision(boat, boat.getBoundingBox())) return InteractionResultHolder.fail(heldItem);

            if (!level.isClientSide) {
                level.addFreshEntity(boat);
                level.gameEvent(player, GameEvent.ENTITY_PLACE, hitResult.getLocation());
                if (!player.getAbilities().instabuild) heldItem.shrink(1);
            }

            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.sidedSuccess(heldItem, level.isClientSide());
        }

        protected Boat getBoat(Level level, HitResult hitResult) {
            if (hasChest) {
                var boat = new CustomBoat.WithChest(level, hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z);
                boat.setVariant(variant);
                return boat;
            }
            var boat = new CustomBoat(level, hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z);
            boat.setVariant(variant);
            return boat;
        }
    }


    public static class WithChest extends ChestBoat {

        public static final String ID = "chest_boat";
        public static RegistryObject<EntityType<CustomBoat.WithChest>> ENTITY_TYPE;

        protected static final EntityDataAccessor<String> DATA_VARIANT = SynchedEntityData.defineId(CustomBoat.WithChest.class, EntityDataSerializers.STRING);
        protected static final String DEFAULT_VARIANT = ChthonicYew.ID;

        public WithChest(EntityType<CustomBoat.WithChest> entityType, Level level) {
            super(entityType, level);
        }

        public WithChest(Level level, double x, double y, double z) {
            super(ENTITY_TYPE.get(), level);
            setPos(x, y, z);
            this.xo = x;
            this.yo = y;
            this.zo = z;
        }


        @Override
        public Item getDropItem() {
            return Registry.getItem(getCustomVariant() + "_chest_boat");
        }

        @Override
        protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
            lastYd = this.getDeltaMovement().y;
            if (isPassenger()) return;
            if (onGround) {
                if (fallDistance > 3.0F) handleFallDamage();
                resetFallDistance();
            } else if (!canBoatInFluid(level().getFluidState(blockPosition().below())) && y < 0.0D) {
                fallDistance -= (float) y;
            }
        }
        protected void handleFallDamage() {
            if (status != Boat.Status.ON_LAND) {
                resetFallDistance();
                return;
            }

            causeFallDamage(fallDistance, 1, damageSources().fall());
            if (level().isClientSide || isRemoved()) return;

            kill();
            if (level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                for (int i = 0; i < 3; ++i) spawnAtLocation(getPlanks());
                for (int j = 0; j < 2; ++j) spawnAtLocation(Items.STICK);
            }
        }


        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            entityData.define(DATA_VARIANT, DEFAULT_VARIANT);
        }

        public void setVariant(String ID) {
            entityData.set(DATA_VARIANT, (ID != null && !ID.isEmpty()) ? ID : DEFAULT_VARIANT);
        }
        public String getCustomVariant() {
            return entityData.get(DATA_VARIANT);
        }
        public Block getPlanks() {
            return Registry.getBlock(getCustomVariant() + "_planks");
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {
            tag.putString("Type", getCustomVariant());
            addChestVehicleSaveData(tag);
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {
            if (tag.contains("Type", CompoundTag.TAG_STRING)) {
                setVariant(tag.getString("Type"));
            }
            readChestVehicleSaveData(tag);
        }
    }


    @OnlyIn(Dist.CLIENT)
    public static class Renderer extends BoatRenderer {
        private final Map<String, Pair<ResourceLocation, ListModel<Boat>>> boatResources;

        public Renderer(EntityRendererProvider.Context context, boolean hasChest) {
            super(context, hasChest);
            boatResources = ALL_VARIANTS.stream().collect(ImmutableMap.toImmutableMap(type -> type,
                    type -> Pair.of(WindowBox.RL(getTextureLocation(type, hasChest)), createBoatModel(context, type, hasChest))));

        }

        private static String getTextureLocation(String ID, boolean hasChest) {
            return hasChest ? "textures/entity/chest_boat/" + ID + ".png" : "textures/entity/boat/" + ID + ".png";
        }

        private ListModel<Boat> createBoatModel(EntityRendererProvider.Context context, String ID, boolean hasChest) {
            var modelLayerRL = hasChest ? createChestBoatModelName(ID) : createBoatModelName(ID);
            var modelPart = context.bakeLayer(modelLayerRL);
            return hasChest ? new ChestBoatModel(modelPart) : new BoatModel(modelPart);
        }

        public static ModelLayerLocation createBoatModelName(String ID) {
            return new ModelLayerLocation(WindowBox.RL("boat/" + ID), "main");
        }

        public static ModelLayerLocation createChestBoatModelName(String ID) {
            return new ModelLayerLocation(WindowBox.RL("chest_boat/" + ID), "main");
        }

        @Override
        public @NotNull Pair<ResourceLocation, ListModel<Boat>> getModelWithLocation(@NotNull Boat boat) {
            if (boat instanceof CustomBoat customBoat) return boatResources.get(customBoat.getCustomVariant());
            if (boat instanceof CustomBoat.WithChest customChestBoat) return boatResources.get(customChestBoat.getCustomVariant());
            return boatResources.get(DEFAULT_VARIANT);
        }
    }
}