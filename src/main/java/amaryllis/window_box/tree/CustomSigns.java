package amaryllis.window_box.tree;

import amaryllis.window_box.Registry;
import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.flower.functional.Dispelagonium;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import vazkii.botania.client.render.block_entity.SpecialFlowerBlockEntityRenderer;

import java.util.ArrayList;
import java.util.List;

import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.Registry.RegisterBlockOnly;
import static amaryllis.window_box.Registry.RegisterItem;
import static amaryllis.window_box.Registry.getBlock;
import static amaryllis.window_box.Registry.propOf;

public class CustomSigns {

    public static final List<String> ALL_VARIANTS = new ArrayList<>();

    public static void register() {
        List<String> allSigns = new ArrayList<>();
        List<String> allHangingSigns = new ArrayList<>();
        ALL_VARIANTS.forEach(ID -> {
            allSigns.add(ID + "_sign");
            allSigns.add(ID + "_wall_sign");
            allHangingSigns.add(ID + "_hanging_sign");
            allHangingSigns.add(ID + "_wall_hanging_sign");
        });
        Registry.RegisterBlockEntityType(CustomSignBlockEntity.ID, CustomSignBlockEntity::new, allSigns);
        Registry.RegisterBlockEntityType(CustomHangingSignBlockEntity.ID, CustomHangingSignBlockEntity::new, allHangingSigns);
        RegisterBlockEntityRenderer(CustomSignBlockEntity.ID, SignRenderer::new);
        RegisterBlockEntityRenderer(CustomHangingSignBlockEntity.ID, HangingSignRenderer::new);
    }

    public static void RegisterVariant(String ID, WoodType woodType) {
        ALL_VARIANTS.add(ID);

        RegisterBlockOnly(ID + "_sign", () -> new CustomSigns.Standing(propOf(Blocks.OAK_SIGN), woodType));
        RegisterBlockOnly(ID + "_wall_sign", () -> new CustomSigns.Wall(propOf(Blocks.OAK_WALL_SIGN), woodType));
        RegisterItem(ID + "_sign", () -> new SignItem(new Item.Properties().stacksTo(16), getBlock(ID + "_sign"), getBlock(ID + "_wall_sign")));

        RegisterBlockOnly(ID + "_hanging_sign", () -> new CustomSigns.HangingCeiling(propOf(Blocks.OAK_HANGING_SIGN), woodType));
        RegisterBlockOnly(ID + "_wall_hanging_sign", () -> new CustomSigns.HangingWall(propOf(Blocks.OAK_WALL_HANGING_SIGN), woodType));
        RegisterItem(ID + "_hanging_sign", () -> new HangingSignItem(getBlock(ID + "_hanging_sign"), getBlock(ID + "_wall_hanging_sign"), new Item.Properties().stacksTo(16)));
    }

    public static class Standing extends StandingSignBlock {
        public Standing(Properties properties, WoodType type) { super(properties, type); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CustomSignBlockEntity(pos, state); }
    }
    public static class Wall extends WallSignBlock {
        public Wall(Properties properties, WoodType type) { super(properties, type); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CustomSignBlockEntity(pos, state); }
    }

    public static class HangingCeiling extends CeilingHangingSignBlock {
        public HangingCeiling(Properties properties, WoodType type) { super(properties, type); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CustomHangingSignBlockEntity(pos, state); }
    }
    public static class HangingWall extends WallHangingSignBlock {
        public HangingWall(Properties properties, WoodType type) { super(properties, type); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CustomHangingSignBlockEntity(pos, state); }
    }


    public static class CustomSignBlockEntity extends SignBlockEntity {
        public static final String ID = "sign";

        public CustomSignBlockEntity(BlockPos pos, BlockState state) { super(Registry.getBlockEntityType(ID), pos, state); }
        @Override public BlockEntityType<?> getType() { return Registry.getBlockEntityType(ID); }
    }

    public static class CustomHangingSignBlockEntity extends HangingSignBlockEntity {
        public static final String ID = "hanging_sign";

        public CustomHangingSignBlockEntity(BlockPos pos, BlockState state) { super(Registry.getBlockEntityType(ID), pos, state); }
        @Override public BlockEntityType<?> getType() { return Registry.getBlockEntityType(ID); }
    }

}
