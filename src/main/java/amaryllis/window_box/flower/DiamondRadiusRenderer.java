package amaryllis.window_box.flower;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.api.block_entity.SpecialFlowerBlockEntity;
import vazkii.botania.client.core.handler.ClientTickHandler;
import vazkii.botania.client.core.helper.RenderHelper;
import vazkii.botania.client.render.block_entity.FloatingFlowerBlockEntityRenderer;
import vazkii.botania.client.render.block_entity.SpecialFlowerBlockEntityRenderer;
import vazkii.botania.common.item.equipment.bauble.ManaseerMonocleItem;

import java.util.*;

public class DiamondRadiusRenderer<T extends SpecialFlowerBlockEntity> extends SpecialFlowerBlockEntityRenderer<T> {

    protected static final Vector2f CENTER_OFFSET = new Vector2f(0.5f, 0.5f);
    protected static final HashMap<Integer, Vector2i[]> VERTICES = new HashMap<>();


    public static RadiusDescriptor RadiusDescriptorDiamond(BlockPos pos, int radius) {
        return new RadiusDescriptor.Circle(pos, 64 + radius);
    }


    public DiamondRadiusRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SpecialFlowerBlockEntity flower, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (flower.isFloating()) {
            FloatingFlowerBlockEntityRenderer.renderFloatingIsland(flower, partialTicks, ms, buffers, overlay);
        }
        if (!(Minecraft.getInstance().cameraEntity instanceof LivingEntity view)) return;
        if (!ManaseerMonocleItem.hasMonocle(view)) return;

        BlockPos pos = null;
        HitResult ray = Minecraft.getInstance().hitResult;
        if (ray != null && ray.getType() == HitResult.Type.BLOCK) {
            pos = ((BlockHitResult) ray).getBlockPos();
        }

        boolean hasBindingAttempt = hasBindingAttempt(view, flower.getBlockPos());
        if (hasBindingAttempt || flower.getBlockPos().equals(pos)) {
            ms.pushPose();
            if (hasBindingAttempt) ms.translate(0, 0.005, 0);
            renderRadius(flower, ms, buffers, flower.getRadius());
            ms.translate(0, 0.002, 0);
            renderRadius(flower, ms, buffers, flower.getSecondaryRadius());
            ms.popPose();
        }
    }

    public static void renderRadius(BlockEntity tile, PoseStack ms, MultiBufferSource buffers, @Nullable RadiusDescriptor descriptor) {
        if (descriptor != null) {
            ms.pushPose();
            ms.translate(0, RenderHelper.getOffY(), 0);
            if (descriptor instanceof RadiusDescriptor.Circle circle) {
                if (circle.radius() > 64) {
                    renderDiamond(ms, buffers, tile.getBlockPos(), circle.subtileCoords(), (int)circle.radius() - 64);
                } else {
                    renderCircle(ms, buffers, tile.getBlockPos(), circle.subtileCoords(), circle.radius());
                }
            } else if (descriptor instanceof RadiusDescriptor.Rectangle rectangle) {
                renderRectangle(ms, buffers, tile.getBlockPos(), rectangle.aabb());
            }
            RenderHelper.incrementOffY();
            ms.popPose();
        }
    }

    public static void renderDiamond(PoseStack ms, MultiBufferSource buffers, BlockPos tilePos, BlockPos center, int radius) {
        if (!VERTICES.containsKey(radius)) cacheVerticesForRadius(radius);
        var vertices = VERTICES.get(radius);

        ms.pushPose();
        ms.translate(center.getX() - tilePos.getX(), center.getY() - tilePos.getY(), center.getZ() - tilePos.getZ());

        int color = Mth.hsvToRgb(ClientTickHandler.ticksInGame % 200 / 200f, 0.6f, 1);
        int r = (color >> 16 & 0xFF);
        int g = (color >> 8 & 0xFF);
        int b = (color & 0xFF);

        VertexConsumer buffer = buffers.getBuffer(RenderHelper.CIRCLE);
        Matrix4f mat = ms.last().pose();

        float innerScale = (radius - FRAME_WIDTH) / radius;
        renderTriangleFan(buffer, mat, CENTER_OFFSET, vertices, innerScale, Y_OFFSET_INNER, r, g, b, INNER_ALPHA);
        renderTriangleFan(buffer, mat, CENTER_OFFSET, vertices, 1, Y_OFFSET_OUTER, r, g, b, OUTER_ALPHA);

        ms.popPose();
    }

    protected static void renderTriangleFan(VertexConsumer buffer, Matrix4f mat, Vector2f center, Vector2i[] vertices, float scale, float y, int r, int g, int b, int alpha) {
        List<Runnable> rVertices = new ArrayList<>(vertices.length);
        for (Vector2i v: vertices) {
            Vector2f vertex = (Math.abs(scale - 1) < 0.01) ? new Vector2f(v) : new Vector2f(v).add(0.5f, 0.5f).mul(scale).add(-0.5f, -0.5f);
            rVertices.add(() -> buffer.vertex(mat, vertex.x, y, vertex.y).color(r, g, b, alpha).endVertex());
        }
        RenderHelper.triangleFan(() -> buffer.vertex(mat, center.x, y, center.y).color(r, g, b, alpha).endVertex(), rVertices);
    }

    protected static void cacheVerticesForRadius(int radius) {
        List<Vector2i> vertices = new ArrayList<>();
        int x = 0;
        int z = -radius;
        for (int i = 0; i <= radius; i++) {
            vertices.add(new Vector2i(x++, z));
            vertices.add(new Vector2i(x, z++));
        }
        for (int i = 0; i < radius; i++) {
            vertices.add(new Vector2i(x--, z));
            vertices.add(new Vector2i(x, z++));
        }
        for (int i = 0; i <= radius; i++) {
            vertices.add(new Vector2i(x--, z));
            vertices.add(new Vector2i(x, z--));
        }
        for (int i = 0; i < radius; i++) {
            vertices.add(new Vector2i(x++, z));
            vertices.add(new Vector2i(x, z--));
        }
        vertices.add(new Vector2i(0, -radius));
        VERTICES.put(radius, vertices.toArray(new Vector2i[vertices.size()]));
    }
}
