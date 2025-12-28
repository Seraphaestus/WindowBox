package amaryllis.window_box.item;

import amaryllis.window_box.Registry;
import amaryllis.window_box.Util;
import amaryllis.window_box.WindowBox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import vazkii.botania.common.block.BotaniaBlocks;

import java.util.List;
import java.util.function.Consumer;

import static amaryllis.window_box.Registry.RegisterItem;

public class Bouquet extends Item implements CustomItemRenderer.Item, Registry.DefaultCreativeStack {

    public static final String ID = "bouquet";

    public static final float FLOWERS_SCALE = 0.85f;
    public static final float FLOWERS_OFFSET_Y = 0.25f;
    public static final float FLOWERS_Z_OFFSET = Util.PIXEL / 8f;
    public static final ResourceLocation HOLDER = WindowBox.RL("item/bouquet_holder");

    public static final int MAX_FLOWERS = 3;
    public static TagKey<Item> FLOWERS_FOR_BOUQUET = TagKey.create(Registries.ITEM, (WindowBox.RL("flowers_for_bouquet")));

    public static void register() {
        RegisterItem(ID, () -> new Bouquet(new Item.Properties().stacksTo(1)));
    }

    public Bouquet(Properties properties) {
        super(properties);
    }

    public ItemStack createDefaultStack() {
        var stack = new ItemStack(this);
        insertFlower(stack, new ItemStack(BotaniaBlocks.lightBlueShinyFlower), null);
        insertFlower(stack, new ItemStack(BotaniaBlocks.pinkShinyFlower), null);
        insertFlower(stack, new ItemStack(BotaniaBlocks.yellowShinyFlower), null);
        stack.getOrCreateTag().putBoolean("NoTooltip", true);
        return stack;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack bouquet, @NotNull Slot slot, @NotNull ClickAction action, @NotNull Player player) {
        if (bouquet.getCount() != 1 || action != ClickAction.SECONDARY) return false;
        ItemStack stackInSlot = slot.getItem();
        var flowerCount = getFlowerCount(bouquet);
        if (stackInSlot.isEmpty()) {
            if (flowerCount <= 0) return false;
            // Try to deposit a flower into the empty slot
            slot.safeInsert(removeFlower(bouquet, player));
            return true;
        }
        if (isValidFlower(stackInSlot)) {
            if (flowerCount >= MAX_FLOWERS) return false;
            // Take flower from slot and put it in bouquet
            insertFlower(bouquet, slot.safeTake(stackInSlot.getCount(), 1, player), player);
            return true;
        }
        return false;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack bouquet, @NotNull ItemStack stack, @NotNull Slot slot, @NotNull ClickAction action, @NotNull Player player, @NotNull SlotAccess slotAccess) {
        if (bouquet.getCount() != 1 || action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        var flowerCount = getFlowerCount(bouquet);
        if (stack.isEmpty()) {
            if (flowerCount <= 0) return false;
            // Retrieve a flower from the bouquet
            slotAccess.set(removeFlower(bouquet, player));
            return true;
        }
        if (isValidFlower(stack)) {
            if (flowerCount >= MAX_FLOWERS) return false;
            // Deposit flower into the bouquet
            var flower = stack.copyWithCount(1);
            stack.shrink(1);
            insertFlower(bouquet, flower, player);
            return true;
        }
        return false;
    }

    protected static boolean isValidFlower(ItemStack stack) {
        if (stack.isEmpty() || !stack.getItem().canFitInsideContainerItems()) return false;
        return stack.is(FLOWERS_FOR_BOUQUET);
    }
    protected static List<ItemStack> getFlowers(ItemStack bouquet) {
        CompoundTag tag = bouquet.getTag();
        if (tag == null || !tag.contains("Items")) return List.of();
        ListTag itemsTag = tag.getList("Items", Tag.TAG_COMPOUND);
        return itemsTag.stream().map(CompoundTag.class::cast).map(ItemStack::of).filter(stack -> !stack.isEmpty()).toList();
    }
    protected static int getFlowerCount(ItemStack bouquet) {
        CompoundTag tag = bouquet.getTag();
        if (tag == null || !tag.contains("Items")) return 0;
        ListTag itemsTag = tag.getList("Items", Tag.TAG_COMPOUND);
        return itemsTag.size();
    }

    protected ItemStack removeFlower(ItemStack bouquet, Player player) {
        CompoundTag tag = bouquet.getOrCreateTag();
        if (!tag.contains("Items")) return ItemStack.EMPTY;

        ListTag itemsTag = tag.getList("Items", Tag.TAG_COMPOUND);
        if (itemsTag.isEmpty()) return ItemStack.EMPTY;

        CompoundTag firstItemTag = itemsTag.getCompound(0);
        var flower = ItemStack.of(firstItemTag);
        itemsTag.remove(0);
        if (itemsTag.isEmpty()) bouquet.removeTagKey("Items");

        if (player != null) player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
        return flower;
    }
    protected void insertFlower(ItemStack bouquet, ItemStack flower, Player player) {
        if (!isValidFlower(flower)) return;

        CompoundTag tag = bouquet.getOrCreateTag();
        if (!tag.contains("Items")) tag.put("Items", new ListTag());
        ListTag itemsTag = tag.getList("Items", Tag.TAG_COMPOUND);

        CompoundTag newTag = new CompoundTag();
        flower.save(newTag);
        itemsTag.add(0, newTag);

        if (player != null) player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }

    @Override
    public void appendHoverText(ItemStack bouquet, Level level, List<Component> components, TooltipFlag isAdvanced) {
        if (bouquet.hasTag() && bouquet.getTag().contains("NoTooltip")) return;
        for (ItemStack flower: getFlowers(bouquet)) {
            components.add(Component.empty().append(flower.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public void initializeClient(@NotNull Consumer<IClientItemExtensions> consumer) {
        CustomItemRenderer.initializeItem(consumer);
    }

    @Override
    public void render(ItemStack bouquet, ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        ms.pushPose();
        if (displayContext.firstPerson() && isHoldingOutBouquet()) {
            // Holding out bouquet
            ms.translate(0.5, 0, 0.5);
            ms.mulPose(Axis.ZP.rotationDegrees(-15));
            ms.mulPose(Axis.YP.rotationDegrees(60));
            ms.translate(-0.75, 0.25, -1);
        }

        float fMax = 0;
        var flowers = getFlowers(bouquet);
        if (!flowers.isEmpty()) {
            ms.pushPose();
            // Scale flowers and offset vertically
            ms.translate(0.5, 0, 0.5);
            ms.scale(FLOWERS_SCALE, FLOWERS_SCALE, FLOWERS_SCALE);
            ms.translate(-0.5, FLOWERS_OFFSET_Y, -0.5);

            int flowerCount = Math.min(flowers.size(), MAX_FLOWERS);
            fMax = 0.5f * (flowerCount - 1);
            for (int i = 0; i < flowerCount; i++) {
                float f = i - fMax; // f is the index i adjusted around the center, e.g. [0, 1, 2, 3] -> [-1.5, -0.5, 0.5, 1.5]

                ms.pushPose();
                // Rotate flowers
                ms.translate(0.5, 0, 0.5);
                ms.mulPose(Axis.ZP.rotationDegrees(-30 * f));
                ms.translate(-0.5, 0, -0.5);
                // Apply anti z-fighting
                ms.translate(0, 0, FLOWERS_Z_OFFSET * f + FLOWERS_Z_OFFSET * 2 * (fMax - Math.abs(f)));

                renderStack(flowers.get(i), displayContext, ms, buffers, light, overlay);

                ms.popPose();
            }
            ms.popPose();
        }

        // Draw bouquet holder on top
        ms.pushPose();
        if (!flowers.isEmpty()) {
            ms.translate(0, 0, 0.5);
            float depth = Util.PIXEL + FLOWERS_Z_OFFSET * flowers.size();
            ms.scale(1, 1, depth / Util.PIXEL);
            ms.translate(0, 0, -0.5 + FLOWERS_Z_OFFSET);
        }
        tryRenderModel(HOLDER, bouquet, displayContext, ms, buffers, light, overlay);
        ms.popPose();

        ms.popPose();
    }

    protected void tryRenderModel(ResourceLocation modelLocation, ItemStack stack, ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        var modelManager = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getModelManager();
        var model = modelManager.getModel(modelLocation);
        if (model == modelManager.getMissingModel()) return;
        renderStack(stack, model, displayContext, ms, buffers, light, overlay);
    }

    protected void renderStack(ItemStack stack, ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
        renderStack(stack, model, displayContext, ms, buffers, light, overlay);
    }

    @SuppressWarnings("UnstableApiUsage")
    protected void renderStack(ItemStack stack, BakedModel model, ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (model.isCustomRenderer()) {
            var customRenderer = IClientItemExtensions.of(stack).getCustomRenderer();
            customRenderer.renderByItem(stack, displayContext, ms, buffers, light, overlay);
            return;
        }

        var itemRenderer = Minecraft.getInstance().getItemRenderer();
        for (var subModel: model.getRenderPasses(stack, true)) {
            for (var renderType: subModel.getRenderTypes(stack, true)) {
                VertexConsumer vConsumer = ItemRenderer.getFoilBufferDirect(buffers, renderType, true, stack.hasFoil());
                itemRenderer.renderModelLists(subModel, stack, light, overlay, ms, vConsumer);
            }
        }
    }

    public static boolean isHoldingOutBouquet() {
        return Minecraft.getInstance().options.keyUse.isDown();
    }
    public static void poseArm(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        if (isHoldingOutBouquet()) {
            var modelArm = (arm == HumanoidArm.RIGHT) ? model.rightArm : model.leftArm;
            modelArm.xRot = (float) Math.toRadians(-75);
        }
    }


    @Mod.EventBusSubscriber(modid = WindowBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    protected static class ClientEventHandler {

        @SubscribeEvent
        public static void onPlayerRendered(RenderPlayerEvent.Pre event) {
            var player = event.getEntity();
            if (player == null) return;

            var model = event.getRenderer().getModel();
            boolean isRightHanded = Minecraft.getInstance().options.mainHand().get() == HumanoidArm.RIGHT;

            if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof Bouquet) {
                setArmPose(model, true, "HOLD_BOUQUET", isRightHanded);
            }
            if (player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof Bouquet) {
                setArmPose(model, false, "HOLD_BOUQUET", isRightHanded);
            }
        }

        protected static void setArmPose(PlayerModel<AbstractClientPlayer> model, boolean mainHand, String pose, boolean isRightHanded) {
            if (mainHand == isRightHanded) {
                model.rightArmPose = HumanoidModel.ArmPose.valueOf(pose);
            } else {
                model.leftArmPose = HumanoidModel.ArmPose.valueOf(pose);
            }
        }
    }

}
