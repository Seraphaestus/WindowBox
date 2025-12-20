package amaryllis.window_box.flower;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import vazkii.botania.api.BotaniaAPIClient;
import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.api.mana.spark.SparkHelper;
import vazkii.botania.client.core.helper.RenderHelper;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;
import vazkii.botania.common.block.flower.ManastarBlockEntity;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.List;

public class ManastarWandHUD implements vazkii.botania.api.block.WandHUD {
    protected final ManastarBlockEntity flower;

    public final static int MANA_COLOR = 0x0095FF;

    public ManastarWandHUD(ManastarBlockEntity flower) {
        this.flower = flower;
    }

    @Override
    public void renderHUD(GuiGraphics gui, Minecraft mc) {
        ManaPoolBlockEntity pool = getPool();
        if (pool == null) return;

        var linkedPools = getSparks(pool).stream()
                .filter(spark -> spark.getAttachedTile() instanceof ManaPoolBlockEntity)
                .map(spark -> (ManaPoolBlockEntity)spark.getAttachedTile())
                .toList();

        String title;
        int mana = 0;
        int maxMana = 0;

        if (linkedPools.size() > 1) {
            title = I18n.get("misc.window_box.manastar.multiple", linkedPools.size());
            for (var linkedPool: linkedPools) {
                mana += linkedPool.getCurrentMana();
                maxMana += linkedPool.getMaxMana();
            }
        } else {
            title = I18n.get("misc.window_box.manastar.single");
            mana = pool.getCurrentMana();
            maxMana = pool.getMaxMana();
        }

        int centerX = mc.getWindow().getGuiScaledWidth() / 2;
        int centerY = mc.getWindow().getGuiScaledHeight() / 2;
        int width = Math.max(102, mc.font.width(title)) + 4;

        RenderHelper.renderHUDBox(gui, centerX - width / 2, centerY + 8, centerX + width / 2, centerY + 28);

        BotaniaAPIClient.instance().drawSimpleManaHUD(gui, MANA_COLOR, mana, maxMana, title);
    }

    protected List<ManaSpark> getSparks(ManaPoolBlockEntity pool) {
        var spark = pool.getAttachedSpark();
        if (spark == null) return List.of();
        var pos = pool.getBlockPos();
        return SparkHelper.getSparksAround(pool.getLevel(), pos.getX(), pos.getY(), pos.getZ(), spark.getNetwork());
    }

    protected ManaPoolBlockEntity getPool() {
        for (Direction direction: Direction.Plane.HORIZONTAL) {
            BlockPos pos = flower.getEffectivePos().relative(direction);
            if (flower.getLevel().hasChunkAt(pos)) {
                var receiver = XplatAbstractions.INSTANCE.findManaReceiver(flower.getLevel(), pos, direction.getOpposite());
                if (receiver instanceof ManaPoolBlockEntity pool) return pool;
            }
        }
        return null;
    }
}
