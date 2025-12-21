package amaryllis.window_box.flower.functional;

import amaryllis.window_box.*;
import amaryllis.window_box.entity.FakePlayer;
import amaryllis.window_box.entity.LivingMarker;
import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.flower.FlowerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.api.block_entity.SpecialFlowerBlockEntity;
import vazkii.botania.client.render.block_entity.SpecialFlowerBlockEntityRenderer;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.FloatingSpecialFlowerBlock;
import vazkii.botania.common.item.block.SpecialFlowerBlockItem;
import vazkii.botania.xplat.BotaniaConfig;

import java.util.List;
import java.util.function.Supplier;

import static amaryllis.window_box.DataGen.*;
import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.flower.CustomFlower.FLOATING;

@Mod.EventBusSubscriber(modid = WindowBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WitchPupil extends FunctionalFlowerBlockEntity {

    public static final String ID = "witch_pupil";
    public static final String EYE_ITEM_ID = "eye_berries";

    public static final int COST = 5;
    public static final int MAX_EYES = 8;

    // Slightly random viewing angles instead of perfect increments
    public static final float[] VIEW_ANGLES = new float[] { 5, 35, 85, 135, 185, 215, 265, 325 };
    public static final float[] VIEW_HEIGHTS = new float[] { 0, 0.25f, 0.5f, 0.2f, 0, 0.35f, 0.2f, 0.4f };
    public static final int[][] EYE_SETS = new int[][] { // These are indices in the VIEW_ANGLES array
            new int[] { 0, 5 }, // 2 eyes
            new int[] { 0, 3, 5 }, // 3 eyes
            new int[] { 0, 2, 3, 5 }, // 4 eyes
            new int[] { 0, 2, 3, 5, 6 }, // 5 eyes
            new int[] { 0, 2, 3, 5, 6, 7 }, // 6 eyes
            new int[] { 0, 2, 3, 4, 5, 6, 7 }, // 7 eyes
            new int[] { 0, 1, 2, 3, 4, 5, 6, 7 }, // 8 eyes
    };

    public static RegistryObject<SoundEvent> PLUCK_SOUND;
    public static RegistryObject<SoundEvent> SWITCH_SOUND;


    public static void register() {
        RegisterBlockOnly(ID, () -> new WitchPupil.Block(ID, () -> (BlockEntityType<WitchPupil>) getBlockEntityType(ID)));
        RegisterItem(ID, () -> new WitchPupil.Item(getBlock(ID), new Item.Properties()));
        RegisterBlock(FLOATING(ID), () -> new WitchPupil.FloatingBlock(BotaniaBlocks.FLOATING_PROPS, () -> (BlockEntityType<WitchPupil>) getBlockEntityType(ID)));
        FlowerHelper.RegisterPottedFlower(ID, ID + "/" + MAX_EYES);

        CustomFlower.RegisterBlockEntityType(ID, WitchPupil::new);
        RegisterBlockEntityRenderer(ID, SpecialFlowerBlockEntityRenderer<WitchPupil>::new);
        RegisterWandHUD(ID, Client.FUNCTIONAL_FLOWER_HUD);

        CustomFlower.RegisterStewEffect(ID, MobEffects.BLINDNESS, 11);

        RegisterItem(EYE_ITEM_ID, () -> new EyeItem(new Item.Properties().stacksTo(16).food(Foods.CHORUS_FRUIT)));
        ItemModel(EYE_ITEM_ID, Models::basicItem);

        // NB: Manual blockstates
        ItemModel(ID, (models, name) -> Models.basicItem(models, name, WindowBox.RL("amaryllis/window_box/block/" + name + "/" + MAX_EYES)));
        ItemModel(FLOATING(ID), (models, name) -> Models.basicParent(models, name, "amaryllis/window_box/block/" + FLOATING(ID) + "/" + MAX_EYES));
        TagSpecialFlower(ID, CustomFlower.FlowerType.FUNCTIONAL);
        Recipes.FloatingFlower(ID);
        // Custom loot table for retaining eye count
        Registry.MANUAL_BLOCK_LOOT.add(ID);
        Registry.MANUAL_BLOCK_LOOT.add(FLOATING(ID));

        PLUCK_SOUND = Registry.RegisterSound(ID + "_pluck");
        SWITCH_SOUND = Registry.RegisterSound(ID + "_switch");
    }

    public WitchPupil(BlockPos pos, BlockState state) {
        super(getBlockEntityType(ID), pos, state);
    }

    public static InteractionResult PluckEye(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (player.getPersistentData().contains(ID)) return InteractionResult.FAIL;

        int eyes = state.getValue(Block.EYES) - 1;

        if (!level.isClientSide) {
            Vec3 vec3 = Vec3.atLowerCornerWithOffset(pos, 0.5d, 1.01d, 0.5d).offsetRandom(level.getRandom(), 0.7f);
            ItemEntity eye = new ItemEntity(level, vec3.x(), vec3.y(), vec3.z(), EyeItem.create(level, pos));
            eye.setDefaultPickUpDelay();
            level.addFreshEntity(eye);
        }
        level.playSound(null, pos, PLUCK_SOUND.get(), SoundSource.BLOCKS);

        if (eyes > 0) {
            level.setBlock(pos, state.setValue(Block.EYES, eyes), 3); // 1 (cause block update) and 2 (send to client)
        } else {
            level.destroyBlock(pos, false);
            level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
        }

        return InteractionResult.SUCCESS;
    }

    protected static void startSpectating(ServerPlayer player, Entity cameraEntity) {
        var data = Util.GetOrCreateData(player.getPersistentData(), ID);
        data.putByte("original_game_mode", (byte)player.gameMode.getGameModeForPlayer().getId());

        // Unleash any leashed mobs
        for (Mob mob: player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(7, 7, 7))) {
            if (mob.getLeashHolder() == player) mob.dropLeash(true, !player.getAbilities().instabuild);
        }

        var fakePlayer = FakePlayer.createFrom(player, true);
        data.putUUID("fake_player", fakePlayer.getUUID());
        data.putString("original_dimension", player.level().dimension().location().toString());
        // Ideally we only need the original_level, but the pos data serves as a backup if we can't find the FakePlayer
        Util.PutData_PosD(data, "original_", player.level(), player.position());

        data.putUUID("camera_entity", cameraEntity.getUUID());
        data.putString("flower_dimension", cameraEntity.level().dimension().location().toString());

        player.setGameMode(GameType.SPECTATOR);

        if (cameraEntity.level() instanceof ServerLevel cameraLevel && cameraLevel != player.serverLevel()) {
            // Fix inter-dimensional spectating
            player.teleportTo(cameraLevel, cameraEntity.getX(), cameraEntity.getY(), cameraEntity.getZ(), cameraEntity.getYRot(), cameraEntity.getXRot());
        }
        player.setCamera(cameraEntity);
    }

    @SubscribeEvent
    public static void updateSpectating(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!player.getPersistentData().contains(ID)) return;

        var data = player.getPersistentData().getCompound(ID);
        BlockPos pos = player.getCamera().blockPosition();
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (blockEntity != null && blockEntity instanceof WitchPupil flower &&
            player.getCamera() instanceof LivingMarker && flower.getMana() >= COST) {
                // Drain mana while in use
                flower.addMana(-COST);
                flower.sync();
                // Reduce the input cooldown
                int inputCooldown = data.getInt("input_cooldown");
                if (inputCooldown > 0) data.putInt("input_cooldown", inputCooldown - 1);
            return;
        }

        // Kick player if mana is insufficient, or clean up after them if they have manually exited, or if the flower doesn't exist
        endSpectating(player, data);
    }

    protected static void endSpectating(ServerPlayer player, CompoundTag data) {
        LivingMarker cameraEntity = null;
        var flowerLevel = Util.GetData_Level(player.serverLevel(), data, "flower_");
        if (flowerLevel != null && data.contains("camera_entity")) {
            var UUID = data.getUUID("camera_entity");
            cameraEntity = (LivingMarker)flowerLevel.getEntity(UUID);
        }
        if (cameraEntity != null) cameraEntity.kill();
        else {
            WindowBox.LOGGER.error("Could not find LivingMarker that player {} was using to spectate Witch's Pupil", player.getScoreboardName());
        }

        player.setCamera(null);

        if (!tryRestoreToFakePlayer(player, data)) {
            WindowBox.LOGGER.warn("Could not find FakePlayer when restoring player {} from spectating Witch's Pupil", player.getScoreboardName());

            // Restore pos from backup data, then try again to restore FakePlayer
            Util.TeleportPlayerFromData(player, data, "original_");
            player.setGameMode(FakePlayer.getPlayerOriginalGameMode(player));
            Scheduling.onNextTick(() -> {
                tryRestoreToFakePlayer(player, data);
                player.getPersistentData().remove(ID);
            });
        }
    }
    protected static boolean tryRestoreToFakePlayer(ServerPlayer player, CompoundTag data) {
        var originalLevel = Util.GetData_Level(player.serverLevel(), data, "original_");
        if (originalLevel != null && data.contains("fake_player")) {
            var UUID = data.getUUID("fake_player");
            var fakePlayer = (FakePlayer)originalLevel.getEntity(UUID);
            if (fakePlayer != null) fakePlayer.restorePlayer(player);
            return true;
        }
        return false;
    }

    public static void handleControls(LocalPlayer player) {
        var keys = Minecraft.getInstance().options;
        int direction = 0;
        if (keys.keyRight.isDown()) direction = 1;
        else if (keys.keyLeft.isDown()) direction = -1;
        if (direction != 0)
            NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), new ControlsPacket(direction > 0));
    }

    public static void changeEyeViewpoint(ServerPlayer player, boolean cycleForwards) {
        if (!player.getPersistentData().contains(ID) || player.getCamera() == player) return;

        var data = player.getPersistentData().getCompound(ID);
        int cooldown = data.getInt("input_cooldown");
        if (cooldown > 0) return;

        var flowerPos = player.blockPosition();
        BlockState state = player.level().getBlockState(flowerPos);
        int eyes = state.getValue(Block.EYES);
        if (eyes <= 1) return;

        var cameraEntity = player.getCamera();
        var eyeIndex = data.contains("eye_index") ? data.getInt("eye_index") : 0;
        eyeIndex = eyeIndex + (cycleForwards ? 1 : -1);
        data.putInt("eye_index", eyeIndex);
        eyeIndex = Math.floorMod(eyeIndex, eyes);
        eyeIndex = EYE_SETS[eyes - 2][eyeIndex];
        float newAngle = VIEW_ANGLES[eyeIndex];
        float heightOffset = VIEW_HEIGHTS[eyeIndex];

        cameraEntity.setPos(cameraEntity.getX(), flowerPos.getY() + heightOffset, cameraEntity.getZ());
        cameraEntity.setYRot(newAngle);
        cameraEntity.setYHeadRot(newAngle);

        // TODO: FIX: player doesn't see the blindness effect while spectating
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 1, 0, false, false, false));
        Util.playLocalSound(player, SWITCH_SOUND.get(), SoundSource.BLOCKS);
        data.putInt("input_cooldown", 10);
    }

    @Override
    public void tickFlower() {
        super.tickFlower();

        // Display particles showing that the flower is being used, except to players actively spectating
        if (level.isClientSide && getMana() >= COST && level.random.nextInt(2) == 0) {
            if (!Minecraft.getInstance().player.getPersistentData().contains(ID)) return;
            if (LivingMarker.getAtFlower(level, getEffectivePos(), ID) == null) return;
            Util.doActiveWispParticles(this, getColor());
        }
    }


    @Override
    public RadiusDescriptor getRadius() {
        return new RadiusDescriptor.Circle(getEffectivePos(), 0);
    }

    @Override
    public int getColor() {
        return 0xff4488;
    }

    @Override
    public int getMaxMana() {
        return 1000;
    }


    // Custom Block and FloatingBlock class to handle eyes property and interaction
    protected static class Block extends CustomFlower {
        public static final IntegerProperty EYES = IntegerProperty.create("eyes", 1, MAX_EYES);

        public Block(String ID, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType) {
            super(ID, blockEntityType);
            this.registerDefaultState(this.defaultBlockState().setValue(EYES, MAX_EYES));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(EYES);
        }

        @Override
        public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
            return WitchPupil.PluckEye(state, level, pos, player, hand);
        }

    }

    protected static class FloatingBlock extends FloatingSpecialFlowerBlock {

        public FloatingBlock(Properties properties, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType) {
            super(properties, blockEntityType);
            this.registerDefaultState(this.stateDefinition.any().setValue(Block.EYES, MAX_EYES));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(Block.EYES);
        }

        @Override
        public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
            // Replenish eye count from witch's pupil, to preserve the floating island
            var heldItem = player.getItemInHand(hand);
            if (heldItem.is(Registry.getItem(ID)) && state.getValue(Block.EYES) < MAX_EYES) {
                level.setBlockAndUpdate(pos, state.setValue(Block.EYES, MAX_EYES));
                level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS);
                if (!player.getAbilities().instabuild) heldItem.shrink(1);
                return InteractionResult.SUCCESS;
            }

            return WitchPupil.PluckEye(state, level, pos, player, hand);
        }
    }

    // Custom Item class to add second tooltip line
    protected static class Item extends SpecialFlowerBlockItem {

        public Item(net.minecraft.world.level.block.Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public void appendHoverText(@NotNull ItemStack stack, Level world, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
            super.appendHoverText(stack, world, tooltip, flag);

            // Prevent crash when tooltips queried before configs load
            if (BotaniaConfig.client() == null) return;

            if (BotaniaConfig.client().referencesEnabled()) {
                String key = getDescriptionId() + ".reference2";
                MutableComponent lore = Component.translatable(key);
                if (!lore.getString().equals(key)) {
                    tooltip.add(lore.withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
                }
            }
        }

    }

    // Eye Berries item
    protected static class EyeItem extends net.minecraft.world.item.Item {

        public EyeItem(Properties properties) {
            super(properties);
        }

        public static ItemStack create(Level level, BlockPos pos) {
            ItemStack stack = Registry.createStack(EYE_ITEM_ID);

            var tag = stack.getOrCreateTag();
            if (!tag.contains(ID)) tag.put(ID, new CompoundTag());
            tag = tag.getCompound(ID);
            Util.PutData_Pos(tag, "target_", level, pos);

            return stack;
        }

        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            var stack = player.getItemInHand(hand);
            if (!stack.hasTag() || !stack.getTag().contains(ID)) return InteractionResultHolder.fail(stack);

            // Fail if player is already spectating
            if (player.getPersistentData().contains(ID)) return InteractionResultHolder.fail(stack);

            return super.use(level, player, hand);
        }

        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity player) {
            String langKey = stack.getDescriptionId();
            ItemStack newStack = super.finishUsingItem(stack, level, player);
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                // Validate
                var eyeData = stack.getTag().getCompound(ID);
                var targetLevel = Util.GetData_Level(serverLevel, eyeData, "target_");

                var targetPos = Util.GetData_Pos(eyeData, "target_");
                BlockEntity blockEntity = targetLevel.getBlockEntity(targetPos);
                if (blockEntity != null && blockEntity instanceof WitchPupil flower && flower.getBlockState().getValue(Block.EYES) > 0) {
                    if (flower.getMana() >= COST) {
                        var cameraTarget = LivingMarker.createAtFlower(targetLevel, targetPos, ID);
                        startSpectating(serverPlayer, cameraTarget);
                    } else {
                        serverPlayer.displayClientMessage(Component.translatable(langKey + ".insufficient_mana"), true);
                    }
                }
            }
            return newStack;
        }

    }

    @Mod.EventBusSubscriber(modid = WindowBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    protected static class ClientEventHandler {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            var player = Minecraft.getInstance().player;
            if (player != null) WitchPupil.handleControls(player);
        }
    }

    public record ControlsPacket(boolean cycleForwards) {
        public static void encode(ControlsPacket msg, FriendlyByteBuf buf) {
                buf.writeBoolean(msg.cycleForwards);
        }
        public static ControlsPacket decode(FriendlyByteBuf buf) {
            return new ControlsPacket(buf.readBoolean());
        }

        public static void handle(ControlsPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                WitchPupil.changeEyeViewpoint(player, msg.cycleForwards);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
