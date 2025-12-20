package amaryllis.window_box.entity;

import amaryllis.window_box.Registry;
import amaryllis.window_box.WindowBox;
import amaryllis.window_box.flower.functional.WitchPupil;
import com.mojang.authlib.GameProfile;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

@Mod.EventBusSubscriber(modid = WindowBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class FakePlayer extends LivingEntity {

    public static final String ID = "fake_player";

    public static RegistryObject<EntityType<FakePlayer>> ENTITY_TYPE;

    protected ChunkPos prevChunkPos;

    public static void register() {
        ENTITY_TYPE = Registry.ENTITY_TYPES.register(ID,
                () -> EntityType.Builder.of(FakePlayer::new, MobCategory.MISC)
                        .sized(0.6f, 1.8f)
                        .clientTrackingRange(32)
                        .build(ID)
        );
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ENTITY_TYPE.get(), LivingEntity.createLivingAttributes().build());
    }

    protected static final EntityDataAccessor<CompoundTag> DATA_PROFILE = SynchedEntityData.defineId(FakePlayer.class, EntityDataSerializers.COMPOUND_TAG);
    protected GameProfile profile;
    protected PlayerInfo playerInfo;

    // If true, loads chunks in a 3x3 square and restores player when damaged
    protected boolean simulatingRealPlayer = false;

    protected static final EntityDataAccessor<Byte> DATA_VISIBLE_LAYERS = SynchedEntityData.defineId(FakePlayer.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Byte> DATA_MAIN_HAND = SynchedEntityData.defineId(FakePlayer.class, EntityDataSerializers.BYTE);

    protected final NonNullList<ItemStack> handItems = NonNullList.withSize(2, ItemStack.EMPTY);
    protected final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);



    protected FakePlayer(EntityType<FakePlayer> entityType, Level level) {
        super(entityType, level);
    }

    public static FakePlayer createFrom(Player player, boolean simulatingRealPlayer) {
        var level = player.level();
        var instance = new FakePlayer(ENTITY_TYPE.get(), level);
        instance.setProfile(player.getGameProfile());

        byte visibleLayersMask = 0;
        for (var part: PlayerModelPart.values()) {
            if (player.isModelPartShown(part)) visibleLayersMask = (byte)(visibleLayersMask | part.getBit());
        }
        instance.entityData.set(DATA_VISIBLE_LAYERS, visibleLayersMask);
        instance.entityData.set(DATA_MAIN_HAND, (byte)(player.getMainArm() == HumanoidArm.RIGHT ? 1 : 0));

        if (simulatingRealPlayer) {
            instance.simulatingRealPlayer = true;

            // Sync conditions which could cause damage so the player can't cheese
            instance.setAirSupply(player.getAirSupply());
            instance.setSharedFlagOnFire(player.isOnFire());
            instance.setRemainingFireTicks(player.getRemainingFireTicks());
            instance.fallDistance = player.fallDistance;
            moveEnemyAggro(level, player, instance, 64);
        }
        if (player.getAbilities().flying) instance.setNoGravity(true);

        level.addFreshEntity(instance);
        instance.setPos(player.position());
        instance.setRot(player.getYRot(), player.getXRot());
        instance.setYHeadRot(player.getYHeadRot());

        // Sync visual appearance beyond pos-rot
        for (var slot: EquipmentSlot.values()) {
            instance.setItemSlot(slot, player.getItemBySlot(slot));
        }
        instance.setPose(player.getPose());
        instance.setGlowingTag(player.isCurrentlyGlowing());
        instance.setInvisible(player.isInvisible());

        var mount = player.getVehicle();
        if (mount != null && mount.hasPassenger(player)) {
            player.stopRiding();
            instance.startRiding(mount, true);
        }

        return instance;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PROFILE, new CompoundTag());
        entityData.define(DATA_VISIBLE_LAYERS, (byte)0);
        entityData.define(DATA_MAIN_HAND, (byte)1);
    }

    protected void setProfile(GameProfile profile) {
        CompoundTag profileTag = new CompoundTag();
        NbtUtils.writeGameProfile(profileTag, profile);
        entityData.set(DATA_PROFILE, profileTag);

        this.profile = profile;
        playerInfo = new PlayerInfo(profile, false);
    }
    protected GameProfile getProfile() {
        if (profile == null) {
            profile = NbtUtils.readGameProfile(entityData.get(DATA_PROFILE));
            if (profile != null) playerInfo = new PlayerInfo(profile, false);
        }
        return profile;
    }
    protected PlayerInfo getPlayerInfo() {
        if (playerInfo == null) {
            var profile = getProfile();
            if (profile != null) playerInfo = new PlayerInfo(profile, false);
        }
        return playerInfo;
    }


    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel && !chunkPosition().equals(prevChunkPos)) {
            if (prevChunkPos != null) forceChunkLoading(serverLevel, prevChunkPos, false);
            if (shouldLoadChunks()) forceChunkLoading(serverLevel, chunkPosition(), true);
            prevChunkPos = chunkPosition();
        }
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (!level().isClientSide && simulatingRealPlayer) {
            var player = tryRestorePlayer(true);
            if (player != null) {
                player.hurt(source, amount);
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    @Override public boolean canChangeDimensions() { return false; }


    //region Chunk loading
    protected boolean shouldLoadChunks() {
        return simulatingRealPlayer;
    }

    protected void forceChunkLoading(ServerLevel level, ChunkPos origin, boolean add) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                ForgeChunkManager.forceChunk(level, WindowBox.MOD_ID, this, origin.x + x, origin.z + z, add, true);
            }
        }
    }

    @Override
    public void onAddedToWorld() {
        if (level() instanceof ServerLevel serverLevel && shouldLoadChunks()) forceChunkLoading(serverLevel, chunkPosition(), true);
        super.onRemovedFromWorld();
    }

    @Override
    public void onRemovedFromWorld() {
        if (level() instanceof ServerLevel serverLevel) forceChunkLoading(serverLevel, chunkPosition(), false);
        super.onRemovedFromWorld();
    }
    //endregion

    public void restorePlayer(ServerPlayer player) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        player.teleportTo(serverLevel, getX(), getY(), getZ(), getYRot(), getXRot());
        player.setGameMode(getPlayerOriginalGameMode(player));

        var mount = getVehicle();
        if (mount != null && mount.hasPassenger(this)) {
            stopRiding();
            player.startRiding(mount, true);
        }

        player.setYHeadRot(getYHeadRot());
        if (isNoGravity() && player.getAbilities().mayfly) player.getAbilities().flying = true;

        player.getPersistentData().remove(WitchPupil.ID);
        moveEnemyAggro(level(), this, player, 64);
        remove(Entity.RemovalReason.KILLED);
    }
    public Player tryRestorePlayer(boolean onlyIfSpectating) {
        if (profile == null) return null;
        var playerUUID = profile.getId();
        var player = getServer().getPlayerList().getPlayer(playerUUID);
        if (player == null) return null;
        if (!onlyIfSpectating || player.getCamera() != player) {
            restorePlayer(player);
            return player;
        }
        return null;
    }

    public static GameType getPlayerOriginalGameMode(Player player) {
        var data = player.getPersistentData();
        for (String ID: data.getAllKeys()) {
            var tag = data.get(ID);
            if (tag instanceof CompoundTag compoundTag && compoundTag.contains("original_game_mode")) {
                return GameType.byId(compoundTag.getByte("original_game_mode"));
            }
        }
        return GameType.DEFAULT_MODE;
    }

    protected static void moveEnemyAggro(Level level, LivingEntity from, LivingEntity to, int range) {
        level.getEntitiesOfClass(Mob.class, new AABB(from.blockPosition()).inflate(range, range, range)).forEach(mob -> {
            if (mob.getLastHurtByMob() == from) mob.setLastHurtByMob(to);
            if (mob.getTarget() == from) mob.setTarget(to);
        });
    }


    @Override
    public @NotNull Component getDisplayName() {
        var profile = getProfile();
        return profile != null ? Component.literal(profile.getName()) : super.getDisplayName();
    }

    public ResourceLocation getSkinTextureLocation() {
        var playerInfo = getPlayerInfo();
        return playerInfo == null ? DefaultPlayerSkin.getDefaultSkin(getUUID()) : playerInfo.getSkinLocation();
    }

    public boolean isModelPartShown(PlayerModelPart part) {
        return (entityData.get(DATA_VISIBLE_LAYERS) & part.getMask()) == part.getMask();
    }

    @Override
    public @NotNull Iterable<ItemStack> getArmorSlots() {
        return armorItems;
    }

    @Override
    public @NotNull ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> handItems.get(slot.getIndex());
            case ARMOR -> armorItems.get(slot.getIndex());
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        verifyEquippedItem(stack);
        NonNullList<ItemStack> items = (slot.getType() == EquipmentSlot.Type.HAND) ? handItems : armorItems;
        onEquipItem(slot, items.set(slot.getIndex(), stack), stack);
    }

    @Override
    public @NotNull HumanoidArm getMainArm() {
        return entityData.get(DATA_MAIN_HAND) == 0 ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }


    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        if (profile != null) {
            CompoundTag profileTag = new CompoundTag();
            NbtUtils.writeGameProfile(profileTag, getProfile());
            tag.put("Player", profileTag);
        }

        tag.putBoolean("SimulatingRealPlayer", simulatingRealPlayer);

        tag.putByte("ModelVisibleLayers", entityData.get(DATA_VISIBLE_LAYERS));

        tag.putByte("MainHand", entityData.get(DATA_MAIN_HAND));

        ListTag armorItemsTag = new ListTag();
        for (ItemStack stack: armorItems) {
            CompoundTag itemTag = new CompoundTag();
            if (!stack.isEmpty()) stack.save(itemTag);
            armorItemsTag.add(itemTag);
        }
        tag.put("ArmorItems", armorItemsTag);

        ListTag handItemsTag = new ListTag();
        for (ItemStack stack: handItems) {
            CompoundTag itemTag = new CompoundTag();
            if (!stack.isEmpty()) stack.save(itemTag);
            handItemsTag.add(itemTag);
        }
        tag.put("HandItems", handItemsTag);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("Player", Tag.TAG_COMPOUND)) {
            setProfile(NbtUtils.readGameProfile(tag.getCompound("Player")));
        }

        if (tag.contains("SimulatingRealPlayer")) {
            simulatingRealPlayer = tag.getBoolean("SimulatingRealPlayer");
        }

        if (tag.contains("ModelVisibleLayers")) {
            entityData.set(DATA_VISIBLE_LAYERS, tag.getByte("ModelVisibleLayers"));
        }

        if (tag.contains("MainHand")) {
            entityData.set(DATA_MAIN_HAND, tag.getByte("MainHand"));
        }

        if (tag.contains("ArmorItems", Tag.TAG_LIST)) {
            ListTag armorItemsTag = tag.getList("ArmorItems", Tag.TAG_COMPOUND);
            for (int i = 0; i < this.armorItems.size(); ++i) {
                this.armorItems.set(i, ItemStack.of(armorItemsTag.getCompound(i)));
            }
        }

        if (tag.contains("HandItems", Tag.TAG_LIST)) {
            ListTag handItemsTag = tag.getList("HandItems", Tag.TAG_COMPOUND);
            for (int i = 0; i < this.handItems.size(); ++i) {
                this.handItems.set(i, ItemStack.of(handItemsTag.getCompound(i)));
            }
        }
    }
}
