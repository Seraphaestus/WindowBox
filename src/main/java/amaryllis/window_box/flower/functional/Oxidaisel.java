package amaryllis.window_box.flower.functional;

import amaryllis.window_box.Client;
import amaryllis.window_box.DataGen;
import amaryllis.window_box.Util;
import amaryllis.window_box.WindowBox;
import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.flower.DiamondRadiusRenderer;
import amaryllis.window_box.flower.FlowerHelper;
import amaryllis.window_box.recipes.ConversionRecipe;
import amaryllis.window_box.recipes.ConversionRecipeCategory;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.api.recipe.StateIngredient;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.FloatingSpecialFlowerBlock;
import vazkii.botania.common.crafting.StateIngredientHelper;
import vazkii.botania.common.item.block.SpecialFlowerBlockItem;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.flower.CustomFlower.FLOATING;
import static amaryllis.window_box.flower.DiamondRadiusRenderer.RadiusDescriptorDiamond;

public class Oxidaisel extends FunctionalFlowerBlockEntity {

    public static final String ID = "oxidaisel";

    public static final int COST = 100;

    // Slightly randomized sequence while still being non-random
    private static final int ACTIVATION_PERIOD = 128;
    private static final boolean[] ACTIVATION_TIMES = new boolean[ACTIVATION_PERIOD];
    static {
        List.of(8, 15, 20, 25, 35, 41, 46, 59, 61, 68, 75, 81, 90, 110, 111, 124)
                .forEach(index -> ACTIVATION_TIMES[index] = true);
    }

    private static final BlockPos[] POSITIONS = Util.POSITIONS_2M_DIAMOND;

    public static void register() {
        RegisterBlockOnly(ID, () -> new CustomFlower(ID, () -> (BlockEntityType<Oxidaisel>) getBlockEntityType(ID)));
        RegisterItem(ID, () -> new SpecialFlowerBlockItem(getBlock(ID), new Item.Properties()));
        RegisterBlock(FLOATING(ID), () -> new FloatingSpecialFlowerBlock(BotaniaBlocks.FLOATING_PROPS, () -> (BlockEntityType<Oxidaisel>) getBlockEntityType(ID)));
        FlowerHelper.RegisterPottedFlower(ID);

        CustomFlower.RegisterBlockEntityType(ID, Oxidaisel::new);
        RegisterBlockEntityRenderer(ID, DiamondRadiusRenderer<Oxidaisel>::new);
        RegisterWandHUD(ID, Client.FUNCTIONAL_FLOWER_HUD);

        CustomFlower.RegisterStewEffect(ID, MobEffects.POISON, 8);

        DataGen.SpecialFlower(ID, CustomFlower.FlowerType.FUNCTIONAL);
    }

    public Oxidaisel(BlockPos pos, BlockState state) {
        super(getBlockEntityType(ID), pos, state);
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        if (getMana() < COST) return;

        if (level.isClientSide) {
            // Draw particles on oxidizing copper
            for (BlockPos offset: POSITIONS) {
                BlockPos pos = getEffectivePos().offset(offset);
                if (canWeather(pos)) {
                    SparkleParticleData data = SparkleParticleData.sparkle((float) Math.random(), 0f, 0.4f, 0.25f, 5);
                    level.addParticle(data, pos.getX() + Math.random(), pos.getY() + Math.random(), pos.getZ() + Math.random(), 0, 0, 0);
                }
            }
            return;
        }

        // Try oxidize random block in range
        if (ACTIVATION_TIMES[ticksExisted % ACTIVATION_PERIOD]) {
            var pos = getEffectivePos();
            var copperBlocks = Arrays.stream(POSITIONS).map(pos::offset).filter(this::canWeather).toArray();
            if (copperBlocks.length == 0) return;

            var targetPos = (BlockPos)Util.PickRandom(copperBlocks, level.random);
            BlockState blockState = level.getBlockState(targetPos);
            Block block = blockState.getBlock();
            if (!(block instanceof WeatheringCopper copperBlock)) return;

            Optional<BlockState> nextState = copperBlock.getNext(blockState);
            if (nextState.isEmpty()) return;

            // Add extra delay chance for each adjacent copper block
            int adjacentCopper = 0;
            if (isWeatherable(targetPos.north())) adjacentCopper++;
            if (isWeatherable(targetPos.east()))  adjacentCopper++;
            if (isWeatherable(targetPos.south())) adjacentCopper++;
            if (isWeatherable(targetPos.west()))  adjacentCopper++;
            if (isWeatherable(targetPos.above())) adjacentCopper++;
            if (isWeatherable(targetPos.below())) adjacentCopper++;
            if (adjacentCopper > 0 && Math.random() * (adjacentCopper + 1) > 1) return;

            level.setBlockAndUpdate(targetPos, nextState.get());
            addMana(-COST);
            sync();
        }
    }

    protected boolean isWeatherable(BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof WeatheringCopper;
    }
    protected boolean canWeather(BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        return block instanceof WeatheringCopper && WeatheringCopper.getNext(block).isPresent();
    }

    @Override
    public RadiusDescriptor getRadius() {
        return RadiusDescriptorDiamond(getEffectivePos(), 2);
    }

    @Override
    public int getColor() {
        return 0x00b38f;
    }

    @Override
    public int getMaxMana() {
        return COST;
    }

    public static class Recipe extends ConversionRecipe {
        private static final ResourceLocation TYPE_ID = WindowBox.RL(ID);
        @Override public RecipeType<?> getType() { return BuiltInRegistries.RECIPE_TYPE.get(TYPE_ID); }

        public static final RecipeSerializer<Recipe> SERIALIZER = new Recipe.Serializer<>(Recipe.class);
        @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER; }

        public Recipe(ResourceLocation id, StateIngredient input, BlockState output) {
            super(id, input, output);
        }

        public static Recipe create(Block weatheringCopper) {
            var nextBlock = WeatheringCopper.getNext(weatheringCopper);
            if (nextBlock.isEmpty()) return null;
            return new Recipe(
                    WindowBox.RL(ForgeRegistries.BLOCKS.getKey(nextBlock.get()).getPath()),
                    StateIngredientHelper.of(weatheringCopper),
                    nextBlock.get().defaultBlockState()
            );
        }

        @Override
        public void onSet(Level level, BlockPos pos) {
            BrushableBlockEntity blockEntity = (BrushableBlockEntity) level.getBlockEntity(pos);
            ResourceLocation lootTable = WindowBox.RL(ID + "/" + ForgeRegistries.BLOCKS.getKey(output.getBlock()).getPath());
            blockEntity.setLootTable(lootTable, pos.asLong());
        }
    }

    public static class RecipeCategory extends ConversionRecipeCategory<Recipe> {
        public static final mezz.jei.api.recipe.RecipeType<Recipe> TYPE = createRecipeType(ID, Recipe.class);
        @Override public @NotNull mezz.jei.api.recipe.RecipeType<Recipe> getRecipeType() { return TYPE; }

        public RecipeCategory(IGuiHelper guiHelper) { super(guiHelper, ID); }
    }
}
