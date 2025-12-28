package amaryllis.window_box.flower.functional;

import amaryllis.window_box.*;
import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.flower.FlowerHelper;
import amaryllis.window_box.recipes.ConversionRecipe;
import amaryllis.window_box.recipes.ConversionRecipeCategory;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.api.block_entity.SpecialFlowerBlockEntity;
import vazkii.botania.api.recipe.StateIngredient;
import vazkii.botania.client.render.block_entity.SpecialFlowerBlockEntityRenderer;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.FloatingSpecialFlowerBlock;
import vazkii.botania.common.crafting.StateIngredientHelper;
import vazkii.botania.common.item.block.SpecialFlowerBlockItem;
import vazkii.botania.xplat.BotaniaConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.Registry.ClientOnly.RegisterBlockEntityRenderer;
import static amaryllis.window_box.flower.CustomFlower.FLOATING;
import static net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn;

public class Groundberry extends FunctionalFlowerBlockEntity {

    public static final String ID = "groundberry";
    public static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public static final int COST = 500;
    public static final int DELAY = 3 * Util.SECONDS;
    public static final int RANGE = 5;
    public static final int RANGE_Y = 3;

    protected static HashMap<Block, BrushableBlock> RECIPES = new HashMap<>();

    public static void register() {
        RegisterBlockOnly(ID, () -> new BushBlock(ID, () -> (BlockEntityType<Groundberry>) getBlockEntityType(ID),
            CustomFlower.noOffset()).setShape(SHAPE));
        RegisterItem(ID, () -> new SpecialFlowerBlockItem(getBlock(ID), new Item.Properties()));
        RegisterBlock(FLOATING(ID), () -> new FloatingSpecialFlowerBlock(BotaniaBlocks.FLOATING_PROPS, () -> (BlockEntityType<Groundberry>) getBlockEntityType(ID)));
        FlowerHelper.RegisterPottedFlower(ID);

        CustomFlower.RegisterBlockEntityType(ID, Groundberry::new);
        unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            RegisterBlockEntityRenderer(ID, SpecialFlowerBlockEntityRenderer<Groundberry>::new);
            RegisterWandHUD(ID, Client.FUNCTIONAL_FLOWER_HUD);
        });

        CustomFlower.RegisterStewEffect(ID, MobEffects.DARKNESS, 3);

        DataGen.SpecialFlower(ID, CustomFlower.FlowerType.FUNCTIONAL);
    }

    public static void cacheRecipes() {
        ForgeRegistries.BLOCKS.getValues().stream().filter(block -> block instanceof BrushableBlock).forEach(block ->
                RECIPES.put(((BrushableBlock)block).getTurnsInto(), (BrushableBlock)block)
        );
    }

    public Groundberry(BlockPos pos, BlockState state) {
        super(getBlockEntityType(ID), pos, state);
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        if (level.isClientSide || getMana() < COST) return;

        if (ticksExisted % DELAY == 0) {
            BlockPos pos = getTarget();
            if (pos == null) return;

            BrushableBlock newBlock = RECIPES.get(level.getBlockState(pos).getBlock());
            BlockState newState = newBlock.defaultBlockState();
            if (level.setBlockAndUpdate(pos, newState)) {
                // Add loot table
                BrushableBlockEntity blockEntity = (BrushableBlockEntity) level.getBlockEntity(pos);
                ResourceLocation lootTable = WindowBox.RL(ID + "/" + ForgeRegistries.BLOCKS.getKey(newBlock).getPath());
                blockEntity.setLootTable(lootTable, pos.asLong());
                // Feedback
                if (BotaniaConfig.common().blockBreakParticles())
                    level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(newState));
                level.playSound(null, pos, newBlock.getBrushSound(), SoundSource.BLOCKS);
                //
                addMana(-COST);
                level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
                sync();
            }
        }
    }

    protected boolean isValidTarget(Level level, BlockPos pos) {
        return RECIPES.containsKey(level.getBlockState(pos).getBlock());
    }

    protected BlockPos getTarget() {
        List<BlockPos> targetPositions = new ArrayList<>();
        for (BlockPos pos: BlockPos.betweenClosed(getEffectivePos().offset(-RANGE, -RANGE_Y, -RANGE), getEffectivePos().offset(RANGE, -1, RANGE))) {
            if (isValidTarget(level, pos)) targetPositions.add(pos.immutable());
        }

        if (targetPositions.isEmpty()) return null;
        return Util.PickRandom(targetPositions, level.random);
    }

    @Override
    public RadiusDescriptor getRadius() {
        return RadiusDescriptor.Rectangle.square(getEffectivePos(), RANGE);
    }

    @Override
    public int getColor() {
        return 0xffcc66;
    }

    @Override
    public int getMaxMana() {
        return COST;
    }


    protected static class BushBlock extends CustomFlower {

        public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, WindowBox.RL("groundberry"));

        public BushBlock(String ID, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType, BlockBehaviour.Properties properties) {
            super(ID, blockEntityType, properties);
        }

        @Override
        public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            if (entity instanceof LivingEntity && entity.getType() != EntityType.FOX && entity.getType() != EntityType.BEE) {
                entity.makeStuckInBlock(state, new Vec3(0.8, 0.75, 0.8));
                if (level.isClientSide || (entity.xOld == entity.getX() && entity.zOld == entity.getZ())) return;

                double deltaX = Math.abs(entity.getX() - entity.xOld);
                double deltaZ = Math.abs(entity.getZ() - entity.zOld);
                if (deltaX >= 0.003 || deltaZ >= 0.003) {
                    DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE));
                    entity.hurt(source, 1f);
                }
            }
        }
    }

    public static class Recipe extends ConversionRecipe {
        private static final ResourceLocation TYPE_ID = WindowBox.RL(ID);
        @Override public RecipeType<?> getType() { return BuiltInRegistries.RECIPE_TYPE.get(TYPE_ID); }

        public static final RecipeSerializer<Recipe> SERIALIZER = new Recipe.Serializer<>(Recipe.class);
        @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER; }

        public Recipe(ResourceLocation id, StateIngredient input, BlockState output) {
            super(id, input, output);
        }

        public static Recipe create(BrushableBlock suspiciousBlock) {
            return new Groundberry.Recipe(
                    WindowBox.RL(ForgeRegistries.BLOCKS.getKey(suspiciousBlock).getPath()),
                    StateIngredientHelper.of(suspiciousBlock.getTurnsInto()),
                    suspiciousBlock.defaultBlockState()
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
