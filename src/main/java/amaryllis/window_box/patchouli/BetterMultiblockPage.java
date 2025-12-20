package amaryllis.window_box.patchouli;

import amaryllis.window_box.DataGen;
import amaryllis.window_box.Registry;
import amaryllis.window_box.WindowBox;
import amaryllis.window_box.flower.functional.Dispelagonium;
import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.data.ForgeBlockTagsProvider;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import vazkii.patchouli.api.IMultiblock;
import vazkii.patchouli.api.PatchouliAPI;
import vazkii.patchouli.client.base.ClientTicker;
import vazkii.patchouli.client.book.BookContentsBuilder;
import vazkii.patchouli.client.book.BookEntry;
import vazkii.patchouli.client.book.ClientBookRegistry;
import vazkii.patchouli.client.book.LiquidBlockVertexConsumer;
import vazkii.patchouli.client.book.gui.GuiBook;
import vazkii.patchouli.client.book.page.abstr.PageWithText;
import vazkii.patchouli.common.multiblock.AbstractMultiblock;
import vazkii.patchouli.common.multiblock.MultiblockRegistry;
import vazkii.patchouli.common.multiblock.SerializedMultiblock;
import vazkii.patchouli.xplat.IClientXplatAbstractions;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class BetterMultiblockPage extends PageWithText {

    // Adds extra options for how it's displayed on the page

    // Largely copied from Patchouli's default PageMultiblock type
    // because it's all private instead of protected and for some reason accesstransformers weren't working

    public static void register() {
        ClientBookRegistry.INSTANCE.pageTypes.put(WindowBox.RL("multiblock"), BetterMultiblockPage.class);
    }

    protected static final RandomSource RAND = RandomSource.createNewThreadLocalInstance();

    String name = "";
    @SerializedName("multiblock_id") ResourceLocation multiblockId;
    @SerializedName("multiblock") SerializedMultiblock serializedMultiblock;
    @SerializedName("scale") float scale = 1f;
    @SerializedName("rotate") float rotate = DYNAMIC_ROTATION;
    protected static final float DYNAMIC_ROTATION = Float.POSITIVE_INFINITY;
    @SerializedName("offset_y") float offsetY = 0f;
    @SerializedName("block_scale") float blockScale = 1f;

    protected transient AbstractMultiblock multiblockObj;

    @Override
    public void build(Level level, BookEntry entry, BookContentsBuilder builder, int pageNum) {
        super.build(level, entry, builder, pageNum);
        if (multiblockId != null) {
            IMultiblock mb = MultiblockRegistry.MULTIBLOCKS.get(multiblockId);
            if (mb instanceof AbstractMultiblock) multiblockObj = (AbstractMultiblock) mb;
        }
        if (multiblockObj == null && serializedMultiblock != null) {
            multiblockObj = serializedMultiblock.toMultiblock();
        }
        if (multiblockObj == null) {
            throw new IllegalArgumentException("No multiblock located for " + multiblockId);
        }
    }

    @Override
    public int getTextHeight() {
        return 115;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float pticks) {
        int x = GuiBook.PAGE_WIDTH / 2 - 53;
        int y = 7;
        RenderSystem.enableBlend();
        graphics.setColor(1F, 1F, 1F, 1F);
        GuiBook.drawFromTexture(graphics, book, x, y, 405, 149, 106, 106);

        parent.drawCenteredStringNoShadow(graphics, i18n(name), GuiBook.PAGE_WIDTH / 2, 0, book.headerColor);

        if (multiblockObj != null) renderMultiblock(graphics);

        super.render(graphics, mouseX, mouseY, pticks);
    }

    protected void renderMultiblock(GuiGraphics graphics) {
        multiblockObj.setWorld(mc.level);
        Vec3i size = multiblockObj.getSize();
        int sizeX = size.getX();
        int sizeY = size.getY();
        int sizeZ = size.getZ();
        float maxX = 90;
        float maxY = 90;
        float diag = (float) Math.sqrt(sizeX * sizeX + sizeZ * sizeZ);
        float scale = this.scale * -Math.min(maxX / diag, maxY / sizeY);

        int xPos = GuiBook.PAGE_WIDTH / 2;
        int yPos = 60;
        graphics.pose().pushPose();
        graphics.pose().translate(xPos, yPos, 100);
        graphics.pose().scale(scale, scale, scale);
        graphics.pose().translate(-(float) sizeX / 2, -(float) sizeY / 2 + offsetY, 0);

        // Initial eye pos somewhere off in the distance in the -Z direction
        Vector4f eye = new Vector4f(0, 0, -100, 1);
        Matrix4f rotMat = new Matrix4f();
        rotMat.identity();

        rotate(graphics, rotMat, Axis.XP, -30);

        float offX = (float) -sizeX / 2;
        float offZ = (float) -sizeZ / 2 + 1;

        graphics.pose().translate(-offX, 0, -offZ);
        if (rotate == DYNAMIC_ROTATION) {
            float time = parent.ticksInBook * 0.5F;
            if (!Screen.hasShiftDown()) time += ClientTicker.partialTicks;
            rotate(graphics, rotMat, Axis.YP, time);
        }
        rotate(graphics, rotMat, Axis.YP, (rotate != DYNAMIC_ROTATION) ? rotate : 45);
        graphics.pose().translate(offX, 0, offZ);

        // Finally apply the rotations
        eye.mul(rotMat);
        renderElements(graphics, multiblockObj, BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(sizeX - 1, sizeY - 1, sizeZ - 1)), eye);

        graphics.pose().popPose();
    }

    protected void rotate(GuiGraphics graphics, Matrix4f rotMat, Axis axis, float degrees) {
        graphics.pose().mulPose(axis.rotationDegrees(degrees));
        rotMat.rotation(axis.rotationDegrees(-degrees));
    }

    protected void renderElements(GuiGraphics graphics, AbstractMultiblock mb, Iterable<? extends BlockPos> blocks, Vector4f eye) {
        graphics.pose().pushPose();
        graphics.setColor(1F, 1F, 1F, 1F);
        graphics.pose().translate(0, 0, -1);

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        doWorldRenderPass(graphics, mb, blocks, buffers, eye);
        doTileEntityRenderPass(graphics, mb, blocks, buffers, eye);

        // todo 1.15 transparency sorting
        buffers.endBatch();
        graphics.pose().popPose();
    }

    protected void doWorldRenderPass(GuiGraphics graphics, AbstractMultiblock mb, Iterable<? extends BlockPos> blocks, final @NotNull MultiBufferSource.BufferSource buffers, Vector4f eye) {
        for (BlockPos pos: blocks) {
            BlockState bs = mb.getBlockState(pos);
            graphics.pose().pushPose();

            graphics.pose().translate(pos.getX(), pos.getY(), pos.getZ());

            if (Math.abs(blockScale - 1.0) > 0.01f) {
                graphics.pose().translate(0.5f, 0.5f, 0.5f);
                graphics.pose().scale(blockScale, blockScale, blockScale);
                graphics.pose().translate(-0.5f, -0.5f, -0.5f);
            }

            final FluidState fluidState = bs.getFluidState();
            final BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
            if (!fluidState.isEmpty()) {
                final RenderType layer = ItemBlockRenderTypes.getRenderLayer(fluidState);
                final VertexConsumer buffer = buffers.getBuffer(layer);
                blockRenderer.renderLiquid(pos, mb, new LiquidBlockVertexConsumer(buffer, graphics.pose(), pos), bs, fluidState);
            }
            IClientXplatAbstractions.INSTANCE.renderForMultiblock(bs, pos, mb, graphics.pose(), buffers, RAND);
            graphics.pose().popPose();
        }
    }

    // Hold errored TEs weakly, this may cause some dupe errors but will prevent spamming it every frame
    protected final transient Set<BlockEntity> erroredTiles = Collections.newSetFromMap(new WeakHashMap<>());

    protected void doTileEntityRenderPass(GuiGraphics graphics, AbstractMultiblock mb, Iterable<? extends BlockPos> blocks, MultiBufferSource buffers, Vector4f eye) {
        for (BlockPos pos: blocks) {
            BlockEntity te = mb.getBlockEntity(pos);
            if (te != null && !erroredTiles.contains(te)) {
                // Doesn't take pos anymore, maybe a problem?
                te.setLevel(mc.level);

                // fake cached state in case the renderer checks it as we don't want to query the actual world
                te.setBlockState(mb.getBlockState(pos));

                graphics.pose().pushPose();
                graphics.pose().translate(pos.getX(), pos.getY(), pos.getZ());
                try {
                    BlockEntityRenderer<BlockEntity> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(te);
                    if (renderer != null) {
                        renderer.render(te, ClientTicker.partialTicks, graphics.pose(), buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
                    }
                } catch (Exception e) {
                    erroredTiles.add(te);
                    PatchouliAPI.LOGGER.error("An exception occured rendering tile entity", e);
                } finally {
                    graphics.pose().popPose();
                }
            }
        }
    }
}
