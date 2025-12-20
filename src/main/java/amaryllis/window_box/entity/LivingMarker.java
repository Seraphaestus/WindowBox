package amaryllis.window_box.entity;

import amaryllis.window_box.Registry;
import amaryllis.window_box.Util;
import amaryllis.window_box.WindowBox;
import amaryllis.window_box.flower.functional.Dispelagonium;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Mod.EventBusSubscriber(modid = WindowBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class LivingMarker extends LivingEntity {

    public static final String ID = "marker";

    public static RegistryObject<EntityType<LivingMarker>> ENTITY_TYPE;

    public static void register() {
        ENTITY_TYPE = Registry.ENTITY_TYPES.register(ID,
                () -> EntityType.Builder.of(LivingMarker::new, MobCategory.MISC)
                        .sized(0.8f, 0.8f)
                        .clientTrackingRange(10)
                        .fireImmune()
                        .build(ID)
        );
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ENTITY_TYPE.get(), LivingEntity.createLivingAttributes().build());
    }

    public LivingMarker(EntityType<LivingMarker> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setSilent(true);
        this.setInvulnerable(true);
        this.setInvisible(true);
    }

    public static LivingMarker create(Level level, Vec3 position, String ID) {
        var instance = new LivingMarker(ENTITY_TYPE.get(), level);
        instance.setPos(position.x, position.y, position.z);
        instance.addTag(ID);
        level.addFreshEntity(instance);
        return instance;
    }

    public static LivingMarker createAtFlower(Level level, BlockPos pos, String ID) {
        return LivingMarker.create(level, Util.getFlowerCenter(level, pos), ID);
    }

    public static LivingMarker getOrCreateAtFlower(Level level, BlockPos pos, String ID) {
        var marker = getAtFlower(level, pos, ID);
        if (marker != null) return marker;
        return LivingMarker.createAtFlower(level, pos, ID);
    }

    public static LivingMarker getAtFlower(Level level, BlockPos pos, String ID) {
        var bounds = AABB.ofSize(Util.getFlowerCenter(level, pos), 0.25, 0.25, 0.25);
        for (LivingMarker marker: level.getEntitiesOfClass(LivingMarker.class, bounds)) {
            if (level.isClientSide || marker.getTags().contains(ID)) return marker;
        }
        return null;
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case 60: return; // Cancel death poof particles
            default:
                super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        if (getTags().contains(Dispelagonium.ID)) {
            return !effectInstance.isAmbient() && !Dispelagonium.EFFECT_BLACKLIST.containsKey(effectInstance.getEffect());
        }
        return false;
    }

    @Override public boolean canChangeDimensions() { return false; }

    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(@NotNull Entity entity) {}
    @Override public @NotNull PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }
    @Override public boolean isIgnoringBlockTriggers() { return true; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean skipAttackInteraction(@NotNull Entity entity) { return true; }
    @Override public void knockback(double strength, double x, double z) {}

    @Override public @NotNull Iterable<ItemStack> getArmorSlots() { return List.of(); }
    @Override public @NotNull ItemStack getItemBySlot(@NotNull EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void setItemSlot(@NotNull EquipmentSlot slot, @NotNull ItemStack stack) {}
    @Override public @NotNull HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }
}
