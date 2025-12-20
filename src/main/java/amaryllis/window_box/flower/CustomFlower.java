package amaryllis.window_box.flower;

import amaryllis.window_box.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import vazkii.botania.api.block_entity.SpecialFlowerBlockEntity;
import vazkii.botania.forge.block.ForgeSpecialFlowerBlock;

import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

public class CustomFlower extends ForgeSpecialFlowerBlock {

    public enum FlowerType { MISC, FUNCTIONAL, GENERATING }

    public static final VoxelShape SHAPE = box(4.8, 0, 4.8, 12.8, 16, 12.8);
    public VoxelShape shape = SHAPE;

    public static HashMap<String, Tuple<MobEffect, Integer>> STEW_EFFECTS = new HashMap<>();
    public static void RegisterStewEffect(String ID, MobEffect effect, int duration) {
        STEW_EFFECTS.put(ID, new Tuple<>(effect, duration));
    }

    public static String FLOATING(String ID) { return "floating_" + ID; }
    public static String NON_FLOATING(String ID) { return ID.replace("floating_", ""); }

    public static void RegisterBlockEntityType(String ID, BlockEntityType.BlockEntitySupplier<? extends BlockEntity> constructor) {
        Registry.RegisterBlockEntityType(ID, constructor, List.of(ID, FLOATING(ID)));
    }

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.copy(Blocks.POPPY);
    }
    public static BlockBehaviour.Properties noOffset() {
        return defaultProperties().offsetType(OffsetType.NONE);
    }

    public CustomFlower(String ID, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType) {
        this(ID, blockEntityType, defaultProperties());
    }
    public CustomFlower(String ID, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType, BlockBehaviour.Properties properties) {
        super(STEW_EFFECTS.get(ID).getA(), STEW_EFFECTS.get(ID).getB(), properties, blockEntityType);
    }

    public CustomFlower setShape(VoxelShape shape) {
        this.shape = shape;
        return this;
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter world, @NotNull BlockPos pos, CollisionContext ctx) {
        Vec3 shift = state.getOffset(world, pos);
        return shape.move(shift.x, shift.y, shift.z);
    }
}
