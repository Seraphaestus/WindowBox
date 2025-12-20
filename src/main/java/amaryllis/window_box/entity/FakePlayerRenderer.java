package amaryllis.window_box.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class FakePlayerRenderer extends LivingEntityRenderer<FakePlayer, PlayerModel<FakePlayer>> {

    public FakePlayerRenderer(EntityRendererProvider.Context context) {
        this(context, true);
    }

    public FakePlayerRenderer(EntityRendererProvider.Context context, boolean useSlimModel) {
        super(context, new PlayerModel<>(context.bakeLayer(useSlimModel ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), useSlimModel), 0.5F);

        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(useSlimModel ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(useSlimModel ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
        addLayer(new PlayerItemInHandLayer<>(this, context.getItemInHandRenderer()));
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        addLayer(new ElytraLayer<>(this, context.getModelSet()));
    }

    @Override
    public void render(FakePlayer fakePlayer, float yRot, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light) {
        setModelProperties(fakePlayer);
        super.render(fakePlayer, yRot, partialTicks, ms, buffers, light);
    }

    @Override
    public Vec3 getRenderOffset(FakePlayer fakePlayer, float pPartialTicks) {
        return fakePlayer.isCrouching() ? new Vec3(0.0D, -0.125D, 0.0D) : super.getRenderOffset(fakePlayer, pPartialTicks);
    }

    protected void setModelProperties(FakePlayer fakePlayer) {
        PlayerModel<FakePlayer> model = getModel();
        if (fakePlayer.isSpectator()) {
            model.setAllVisible(false);
            model.head.visible = true;
            model.hat.visible = true;
            return;
        }

        model.setAllVisible(true);
        model.hat.visible = fakePlayer.isModelPartShown(PlayerModelPart.HAT);
        model.jacket.visible = fakePlayer.isModelPartShown(PlayerModelPart.JACKET);
        model.leftPants.visible = fakePlayer.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = fakePlayer.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        model.leftSleeve.visible = fakePlayer.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = fakePlayer.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        model.crouching = fakePlayer.isCrouching();
        HumanoidModel.ArmPose mainArmPose = getArmPose(fakePlayer, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offArmPose = getArmPose(fakePlayer, InteractionHand.OFF_HAND);
        if (mainArmPose.isTwoHanded()) {
            offArmPose = fakePlayer.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }
        if (fakePlayer.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = mainArmPose;
            model.leftArmPose = offArmPose;
        } else {
            model.rightArmPose = offArmPose;
            model.leftArmPose = mainArmPose;
        }

    }

    protected static HumanoidModel.ArmPose getArmPose(FakePlayer fakePlayer, InteractionHand hand) {
        ItemStack stack = fakePlayer.getItemInHand(hand);
        if (stack.isEmpty()) return HumanoidModel.ArmPose.EMPTY;

        if (fakePlayer.getUsedItemHand() == hand && fakePlayer.getUseItemRemainingTicks() > 0) {
            switch (stack.getUseAnimation()) {
                case BLOCK: return HumanoidModel.ArmPose.BLOCK;
                case BOW: return HumanoidModel.ArmPose.BOW_AND_ARROW;
                case SPEAR: return HumanoidModel.ArmPose.THROW_SPEAR;
                case CROSSBOW: return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                case SPYGLASS: return HumanoidModel.ArmPose.SPYGLASS;
                case TOOT_HORN: return HumanoidModel.ArmPose.TOOT_HORN;
                case BRUSH: return HumanoidModel.ArmPose.BRUSH;
            }
        } else if (!fakePlayer.swinging && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }

        HumanoidModel.ArmPose forgeArmPose = IClientItemExtensions.of(stack).getArmPose(fakePlayer, hand, stack);
        if (forgeArmPose != null) return forgeArmPose;

        return HumanoidModel.ArmPose.ITEM;
    }

    public @NotNull ResourceLocation getTextureLocation(FakePlayer fakePlayer) {
        return fakePlayer.getSkinTextureLocation();
    }

    @Override
    protected void scale(FakePlayer fakePlayer, PoseStack ms, float partialTicks) {
        final float scale = 0.9375f;
        ms.scale(scale, scale, scale);
    }

    @Override
    protected void setupRotations(FakePlayer fakePlayer, PoseStack ms, float ageInTicks, float yRot, float partialTicks) {
        float f = fakePlayer.getSwimAmount(partialTicks);
        if (fakePlayer.isFallFlying()) {
            super.setupRotations(fakePlayer, ms, ageInTicks, yRot, partialTicks);

            Vec3 vec3 = fakePlayer.getViewVector(partialTicks);
            Vec3 vec31 = Vec3.ZERO;
            double d0 = vec31.horizontalDistanceSqr();
            double d1 = vec3.horizontalDistanceSqr();
            if (d0 > 0.0D && d1 > 0.0D) {
                double d2 = (vec31.x * vec3.x + vec31.z * vec3.z) / Math.sqrt(d0 * d1);
                double d3 = vec31.x * vec3.z - vec31.z * vec3.x;
                ms.mulPose(Axis.YP.rotation((float)(Math.signum(d3) * Math.acos(d2))));
            }
        } else if (f > 0.0F) {
            super.setupRotations(fakePlayer, ms, ageInTicks, yRot, partialTicks);
            float f0 = fakePlayer.isInWater() || fakePlayer.isInFluidType((fluidType, height) -> fakePlayer.canSwimInFluidType(fluidType)) ? -90.0F - fakePlayer.getXRot() : -90.0F;
            float f1 = Mth.lerp(f, 0.0F, f0);
            ms.mulPose(Axis.XP.rotationDegrees(f1));
            if (fakePlayer.isVisuallySwimming()) {
                ms.translate(0.0F, -1.0F, 0.3F);
            }
        } else {
            super.setupRotations(fakePlayer, ms, ageInTicks, yRot, partialTicks);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class PlayerItemInHandLayer<T extends LivingEntity, M extends EntityModel<T> & ArmedModel & HeadedModel> extends ItemInHandLayer<T, M> {
        private final ItemInHandRenderer itemInHandRenderer;
        private static final float X_ROT_MIN = -(float)Math.PI / 6f;
        private static final float X_ROT_MAX = (float)Math.PI / 2f;

        public PlayerItemInHandLayer(RenderLayerParent<T, M> parentRenderer, ItemInHandRenderer renderer) {
            super(parentRenderer, renderer);
            itemInHandRenderer = renderer;
        }

        protected void renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack ms, MultiBufferSource buffers, int light) {
            if (stack.is(Items.SPYGLASS) && entity.getUseItem() == stack && entity.swingTime == 0) {
                this.renderArmWithSpyglass(entity, stack, arm, ms, buffers, light);
            } else {
                super.renderArmWithItem(entity, stack, context, arm, ms, buffers, light);
            }
        }

        protected void renderArmWithSpyglass(LivingEntity entity, ItemStack stack, HumanoidArm arm, PoseStack ms, MultiBufferSource buffers, int light) {
            ms.pushPose();
            ModelPart modelpart = this.getParentModel().getHead();
            float xRot = modelpart.xRot;
            modelpart.xRot = Mth.clamp(modelpart.xRot, X_ROT_MIN, X_ROT_MAX);
            modelpart.translateAndRotate(ms);
            modelpart.xRot = xRot;
            CustomHeadLayer.translateToHead(ms, false);
            boolean isLeftArm = (arm == HumanoidArm.LEFT);
            ms.translate((isLeftArm ? -2.5F : 2.5F) / 16.0F, -0.0625F, 0.0F);
            this.itemInHandRenderer.renderItem(entity, stack, ItemDisplayContext.HEAD, false, ms, buffers, light);
            ms.popPose();
        }
    }

}
