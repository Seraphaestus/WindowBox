package amaryllis.window_box.item;

import amaryllis.window_box.DataGen;
import amaryllis.window_box.WindowBox;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import vazkii.botania.api.block.Avatar;
import vazkii.botania.api.item.AvatarWieldable;
import vazkii.botania.api.item.BlockProvider;
import vazkii.botania.api.mana.ManaItemHandler;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.common.item.rod.LandsRodItem;
import vazkii.botania.xplat.XplatAbstractions;

import static amaryllis.window_box.Registry.RegisterItem;

public class GrassRod extends LandsRodItem {

    public static final String ID = "grass_rod";

    public static final Block BLOCK = Blocks.GRASS_BLOCK;
    public static final int COST = 100; // Contrast Rod of the Lands' cost of 75

    public static void register() {
        RegisterItem(ID, () -> new GrassRod(new Item.Properties().stacksTo(1)));
        DataGen.ItemModel(ID, DataGen.Models::handheld);
    }

    public GrassRod(Properties props) { super(props); }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        return place(context, BLOCK, COST, 0.05f, 0.35f, 0.1f);
    }

    public static class GrassBlockProvider implements BlockProvider {
        private final ItemStack stack;

        public GrassBlockProvider(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public boolean provideBlock(Player player, ItemStack requestor, Block block, boolean remove) {
            if (block != BLOCK) return false;
            return ManaItemHandler.instance().requestManaExactForTool(requestor, player, COST, remove);
        }

        @Override
        public int getBlockCount(Player player, ItemStack requestor, Block block) {
            if (block != BLOCK) return 0;
            return ManaItemHandler.instance().getInvocationCountForTool(requestor, player, COST);
        }
    }

    public static class AvatarBehavior implements AvatarWieldable {

        private static ResourceLocation overlay = WindowBox.RL("textures/model/avatar_grass.png");

        @Override
        public void onAvatarUpdate(Avatar avatar) {
            BlockEntity blockEntity = (BlockEntity) avatar;
            Level level = blockEntity.getLevel();
            ManaReceiver receiver = XplatAbstractions.INSTANCE.findManaReceiver(level, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, null);
            if (!level.isClientSide && receiver.getCurrentMana() >= COST &&
                avatar.getElapsedFunctionalTicks() % 4 == 0 &&
                level.random.nextInt(8) == 0 &&
                avatar.isEnabled())
            {
                BlockPos pos = blockEntity.getBlockPos().relative(avatar.getAvatarFacing());
                if (level.getBlockState(pos).isAir()) {
                    var state = BLOCK.defaultBlockState();
                    level.setBlockAndUpdate(pos, state);
                    level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
                    receiver.receiveMana(-COST);
                }
            }
        }

        @Override
        public ResourceLocation getOverlayResource(Avatar avatar) {
            return overlay;
        }
    }
}
