package amaryllis.window_box.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.*;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public class CustomItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static final CustomItemRenderer INSTANCE = new CustomItemRenderer();

    public CustomItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static void initializeItem(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CustomItemRenderer.INSTANCE;
            }
        });
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        var item = stack.getItem();
        if (item instanceof CustomItemRenderer.Item customRenderItem) {
            customRenderItem.render(stack, displayContext, ms, buffers, light, overlay);
        } else {
            super.renderByItem(stack, displayContext, ms, buffers, light, overlay);
        }
    }


    public interface Item {
        void render(ItemStack stack, ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffers, int light, int overlay);
    }
}
