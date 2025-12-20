package amaryllis.window_box;

import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.tree.*;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.*;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.ModelProvider;
import net.minecraftforge.common.crafting.conditions.IConditionBuilder;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Quartet;
import vazkii.botania.common.lib.BotaniaTags;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static amaryllis.window_box.flower.CustomFlower.FLOATING;
import static net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition.hasBlockStateProperties;
import static vazkii.botania.data.recipes.CraftingRecipeProvider.conditionsFromItem;

@Mod.EventBusSubscriber(modid = WindowBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGen {

    // List of IDs which might not provide valid entries
    protected static ArrayList<String> OPTIONAL = new ArrayList<>();
    public static void markOptional(String... IDs) { OPTIONAL.addAll(Arrays.asList(IDs)); }


    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper fileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();
    
        // Models
        generator.addProvider(event.includeClient(), new BlockModelsGenerator(packOutput, fileHelper));
        generator.addProvider(event.includeClient(), new ItemModelsGenerator(packOutput, fileHelper));
        
        // Block and Item tags
        BlockTagsGenerator blockTagGenerator = generator.addProvider(event.includeServer(), new BlockTagsGenerator(packOutput, lookupProvider, fileHelper));
        generator.addProvider(event.includeServer(), new ItemTagsGenerator(packOutput, lookupProvider, blockTagGenerator.contentsGetter(), fileHelper));

        // Loot Tables
        generator.addProvider(event.includeServer(), LootTablesGenerator.create(packOutput));
        // Recipes
        generator.addProvider(event.includeServer(), new RecipeGenerator(packOutput));
    }

    // Templates
    public static void SpecialFlower(String ID, CustomFlower.FlowerType type) {
        BlockModel(ID, DataGen.Models::cross);
        ItemModel(ID, (models, name) -> Models.basicItem(models, name, WindowBox.RL("amaryllis/window_box/block/" + name)));
        // Floating variant
        BlockModel(FLOATING(ID), Models::blockManual); // NB: Manual as uses a fancy special loader
        ItemModel(FLOATING(ID), Models::blockParent);
        //
        TagSpecialFlower(ID, type);
        //
        Recipes.FloatingFlower(ID);
    }
    public static void TagSpecialFlower(String ID, CustomFlower.FlowerType type) {
        TagBoth(ID, BlockTags.SMALL_FLOWERS, ItemTags.SMALL_FLOWERS);
        switch (type) {
            case GENERATING -> TagBoth(ID, BotaniaTags.Blocks.GENERATING_SPECIAL_FLOWERS, BotaniaTags.Items.GENERATING_SPECIAL_FLOWERS);
            case FUNCTIONAL -> TagBoth(ID, BotaniaTags.Blocks.FUNCTIONAL_SPECIAL_FLOWERS, BotaniaTags.Items.FUNCTIONAL_SPECIAL_FLOWERS);
            case MISC -> TagBoth(ID, BotaniaTags.Blocks.MISC_SPECIAL_FLOWERS, BotaniaTags.Items.MISC_SPECIAL_FLOWERS);
        }
        // Floating variant
        TagBoth(FLOATING(ID), BotaniaTags.Blocks.SPECIAL_FLOATING_FLOWERS, BotaniaTags.Items.SPECIAL_FLOATING_FLOWERS);
    }

    //region Blockstates and Models
    public static class Models {
        public static ModelFile basicParent(ModelProvider models, String name, String parent) {
            return models.getBuilder(name).parent(ref(WindowBox.MOD_ID, parent));
        }
        public static ModelFile blockParent(ModelProvider models, String name) {
            return basicParent(models, name, "amaryllis/window_box/block/" + name);
        }

        public static ModelFile basicItem(ModelProvider models, String name) {
            return basicItem(models, name, WindowBox.RL("item/" + name));
        }
        public static ModelFile basicItem(ModelProvider models, String name, ResourceLocation texture) {
            return models.withExistingParent(name, "item/generated")
                    .texture("layer0", texture);
        }

        public static ModelFile handheld(ModelProvider models, String name) {
            return models.withExistingParent(name, "item/handheld")
                    .texture("layer0", WindowBox.RL("item/" + name));
        }

        public static ModelFile block(ModelProvider models, String name) {
            return block(models, name, name);
        }
        public static ModelFile block(ModelProvider models, String name, String texture) {
            return models.withExistingParent(name, "amaryllis/window_box/block/cube_all")
                    .texture("all", WindowBox.RL("amaryllis/window_box/block/" + texture));
        }
        public static ModelFile leaves(ModelProvider models, String name, String texture) {
            return models.withExistingParent(name, "amaryllis/window_box/block/leaves")
                    .texture("all", WindowBox.RL("amaryllis/window_box/block/" + texture));
        }
        public static ModelFile cross(ModelProvider models, String name) {
            return cross(models, name, name);
        }
        public static ModelFile cross(ModelProvider models, String name, String texture) {
            return models.getBuilder(name)
                    .parent(ref("botania", "amaryllis/window_box/block/shapes/cross"))
                    .texture("cross", WindowBox.RL("amaryllis/window_box/block/" + texture))
                    .renderType("cutout");
        }
        public static ModelFile flowerPot(ModelProvider models, String name) {
            return flowerPot(models, name, name);
        }
        public static ModelFile flowerPot(ModelProvider models, String name, String texture) {
            return models.withExistingParent("potted_" + name, "amaryllis/window_box/block/flower_pot_cross")
                    .texture("plant", WindowBox.RL("amaryllis/window_box/block/" + texture))
                    .renderType("cutout");
        }

        public static ModelFile ref(String mod, String path) {
            return new ModelFile.UncheckedModelFile(WindowBox.RL(mod, path));
        }
        public static ModelFile blockManual(ModelProvider models, String name) {
            return ref(WindowBox.MOD_ID, "amaryllis/window_box/block/" + name);
        }
        public static ModelFile itemManual(ModelProvider models, String name) {
            return ref(WindowBox.MOD_ID, "item/" + name);
        }
    }

    private static final HashMap<String, BiFunction<ModelProvider, String, ModelFile>> itemModels = new HashMap<>();
    public static void ItemModel(String ID, BiFunction<ModelProvider, String, ModelFile> builder) {
        itemModels.put(ID, builder);
    }
    public static void ItemModel(String ID) {
        itemModels.put(ID, Models::basicItem);
    }

    private static class ItemModelsGenerator extends ItemModelProvider {
        public ItemModelsGenerator(PackOutput output, ExistingFileHelper existingFileHelper) {
            super(output, WindowBox.MOD_ID, existingFileHelper);
        }

        @Override
        protected void registerModels() {
            itemModels.forEach((ID, builder) -> builder.apply(this, ID));
        }
    }

    public enum BlockVariantTypes { LOG, WOOD, STAIRS, SLAB, FENCE, FENCE_GATE, DOOR, TRAPDOOR, BUTTON, PRESSURE_PLATE, SIGN, HANGING_SIGN }
    private static final HashMap<String, BiFunction<ModelProvider, String, ModelFile>> blockModels = new HashMap<>();
    private static final List<Quartet<String, BlockVariantTypes, String, String>> blockVariantsModels = new ArrayList<>();
    public static void BlockModel(String ID, BiFunction<ModelProvider, String, ModelFile> builder) {
        blockModels.put(ID, builder);
    }
    public static void BlockVariantsModel(String ID, BlockVariantTypes type) {
        BlockVariantsModel(ID, type, ID, ID);
    }
    public static void BlockVariantsModel(String ID, BlockVariantTypes type, String texture) {
        BlockVariantsModel(ID, type, texture, texture);
    }
    public static void BlockVariantsModel(String ID, BlockVariantTypes type, String texture, String baseID) {
        blockVariantsModels.add(new Quartet<>(ID, type, texture, baseID));
        switch (type) {
            case DOOR, SIGN, HANGING_SIGN -> itemModels.put(ID, Models::basicItem);
            case FENCE -> itemModels.put(ID, (models, name) ->
                    models.withExistingParent(name, "amaryllis/window_box/block/fence_inventory")
                            .texture("texture", WindowBox.RL("amaryllis/window_box/block/" + texture)));
            case TRAPDOOR -> itemModels.put(ID, (models, name) ->
                    models.withExistingParent(name, WindowBox.RL("amaryllis/window_box/block/" + ID + "_bottom")));
            case BUTTON -> itemModels.put(ID, (models, name) ->
                    models.withExistingParent(name, "amaryllis/window_box/block/button_inventory")
                            .texture("texture", WindowBox.RL("amaryllis/window_box/block/" + texture)));
            default -> itemModels.put(ID, Models::blockParent);
        }
    }

    private static class BlockModelsGenerator extends BlockStateProvider {
        public BlockModelsGenerator(PackOutput packOutput, ExistingFileHelper fileHelper) {
            super(packOutput, WindowBox.MOD_ID, fileHelper);
        }

        @Override
        protected void registerStatesAndModels() {
            blockModels.forEach((ID, builder) -> {
                Block block = Registry.getBlock(ID);
                ModelFile model = builder.apply(models(), ID);
                simpleBlock(block, model);
                if (!itemModels.containsKey(ID)) simpleBlockItem(block, model);
            });
            blockVariantsModels.forEach(data -> {
                Block block = Registry.getBlock(data.getA());
                var texture = WindowBox.prefixRL(WindowBox.RL(data.getC()), "amaryllis/window_box/block/");
                switch (data.getB()) {
                    case LOG -> axisBlock((RotatedPillarBlock) block, texture, WindowBox.extendRL(texture, "_top"));
                    case WOOD -> axisBlock((RotatedPillarBlock) block, texture, texture);
                    case STAIRS -> stairsBlock((StairBlock) block, texture);
                    case SLAB -> slabBlock((SlabBlock) block, WindowBox.prefixRL(WindowBox.RL(data.getD()), "amaryllis/window_box/block/"), texture);
                    case FENCE -> fenceBlock((FenceBlock) block, texture);
                    case FENCE_GATE -> fenceGateBlock((FenceGateBlock) block, texture);
                    case DOOR -> doorBlock((DoorBlock) block, WindowBox.extendRL(texture, "_bottom"), WindowBox.extendRL(texture, "_top"));
                    case TRAPDOOR -> trapdoorBlock((TrapDoorBlock) block, texture, false);
                    case BUTTON -> buttonBlock((ButtonBlock) block, texture);
                    case PRESSURE_PLATE -> pressurePlateBlock((PressurePlateBlock) block, texture);
                    case SIGN -> signBlock((StandingSignBlock) block, (WallSignBlock) Registry.getBlock(data.getD()), texture);
                    case HANGING_SIGN -> hangingSignBlock((CeilingHangingSignBlock) block, (WallHangingSignBlock) Registry.getBlock(data.getD()), texture);
                }
            });
        }

        protected void hangingSignBlock(CeilingHangingSignBlock ceilingBlock, WallHangingSignBlock wallBlock, ResourceLocation texture) {
            ModelFile model = models().sign(ForgeRegistries.BLOCKS.getKey(ceilingBlock).getPath(), texture);
            simpleBlock(ceilingBlock, model);
            simpleBlock(wallBlock, model);
        }
    }
    //endregion

    public static void TagBoth(String ID, TagKey<Block> blockTag, TagKey<Item> itemTag) {
        TagBlock(ID, blockTag);
        TagItem(ID, itemTag);
    }

    //region Item Tags
    private static final HashMap<TagKey<Item>, ArrayList<String>> itemTags = new HashMap<>();
    @SafeVarargs public static void TagItem(String ID, TagKey<Item>... tags) {
        for (TagKey<Item> tag : tags) {
            if (!itemTags.containsKey(tag)) itemTags.put(tag, new ArrayList<>());
            itemTags.get(tag).add(ID);
        }
    }

    private static class ItemTagsGenerator extends ItemTagsProvider {

        public ItemTagsGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagLookup<Block>> blockProvider, @Nullable ExistingFileHelper fileHelper) {
            super(output, lookupProvider, blockProvider, WindowBox.MOD_ID, fileHelper);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            for (TagKey<Item> tag : itemTags.keySet()) {
                var tagAppender = tag(tag);
                itemTags.get(tag).forEach(ID -> {
                    if (OPTIONAL.contains(ID)) tagAppender.addOptional(WindowBox.RL(ID));
                    else tagAppender.add(Registry.getItem(ID));
                });
            }
        }
    }
    //endregion

    //region Block Tags
    private static final HashMap<TagKey<Block>, ArrayList<String>> blockTags = new HashMap<>();
    @SafeVarargs public static void TagBlock(String ID, TagKey<Block>... tags) {
        for (TagKey<Block> tag : tags) {
            if (!blockTags.containsKey(tag)) blockTags.put(tag, new ArrayList<>());
            blockTags.get(tag).add(ID);
        }
    }
    public static void TagBlockNonMovable(String ID) {
        TagBlock(ID, BlockTagsGenerator.FORGE_RELOCATION_NOT_SUPPORTED, BlockTagsGenerator.CREATE_NON_MOVABLE);
    }

    private static class BlockTagsGenerator extends BlockTagsProvider {
        public static final TagKey<Block> FORGE_RELOCATION_NOT_SUPPORTED = BlockTags.create(WindowBox.RL("forge", "relocation_not_supported"));
        public static final TagKey<Block> CREATE_NON_MOVABLE = BlockTags.create(WindowBox.RL("create", "non_movable"));

        public BlockTagsGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper fileHelper) {
            super(output, lookupProvider, WindowBox.MOD_ID, fileHelper);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            for (TagKey<Block> tag : blockTags.keySet()) {
                var tagAppender = tag(tag);
                blockTags.get(tag).forEach(ID -> {
                    if (OPTIONAL.contains(ID)) tagAppender.addOptional(WindowBox.RL(ID));
                    else tagAppender.add(Registry.getBlock(ID));
                });
            }
        }
    }
    //endregion

    //region Loot Tables (Block Drops)
    private static class LootTablesGenerator {
        public static LootTableProvider create(PackOutput output) {
            return new LootTableProvider(output, Set.of(), List.of(
                    new LootTableProvider.SubProviderEntry(Blocks::new, LootContextParamSets.BLOCK)
            ));
        }

        private static class Blocks extends BlockLootSubProvider {
            protected Blocks() {
                super(Set.of(), FeatureFlags.REGISTRY.allFlags());
            }

            @Override
            protected void generate() {
                getKnownBlocks().forEach(block -> {
                    if (block instanceof NaturalStairs) {
                        add(block, LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                            .add(applyExplosionDecay(block, LootItem.lootTableItem(block)
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(0))
                                    .when(hasBlockStateProperties(block).setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(NaturalStairs.PERSISTENT, false))))))));
                    } else if (block instanceof NaturalSlab) {
                        add(block, LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                            .add(applyExplosionDecay(block, LootItem.lootTableItem(block)
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(2))
                                    .when(hasBlockStateProperties(block).setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(SlabBlock.TYPE, SlabType.DOUBLE))))
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(0))
                                    .when(hasBlockStateProperties(block).setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(NaturalSlab.PERSISTENT, false))))))));
                    } else if (block instanceof SlabBlock) {
                        add(block, createSlabItemTable(block));
                    } else if (block instanceof DoorBlock) {
                        add(block, createDoorTable(block));
                    } else if (block instanceof LeavesBlock) {
                        noDropLeaves(block);
                    } else if (block instanceof FlowerPotBlock pottedFlower) {
                        add(block, createPotFlowerItemTable(pottedFlower.getContent()));
                    } else {
                        dropSelf(block);
                    }
                });
            }

            @Override
            protected @NotNull Iterable<Block> getKnownBlocks() {
                return Registry.getBlocksForLootGen()::iterator;
            }

            protected void noDropLeaves(Block leaves) {
                var NORMAL_LEAVES_STICK_CHANCES = new float[]{0.02f, 0.022222223f, 0.025f, 0.033333335f, 0.1f};
                add(leaves, createSilkTouchOrShearsDispatchTable(leaves, ((LootPoolSingletonContainer.Builder)
                        applyExplosionDecay(leaves, LootItem.lootTableItem(Items.STICK)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))))
                        .when(BonusLevelTableCondition.bonusLevelFlatChance(Enchantments.BLOCK_FORTUNE, NORMAL_LEAVES_STICK_CHANCES))));
            }
        }
    }
    //endregion

    //region Recipes
    private static final List<Supplier<RecipeBuilder>> recipes = new ArrayList<>();
    private static final Map<Integer, String> recipeNames = new HashMap<>();
    public static void Recipe(Supplier<RecipeBuilder> builder) {
        recipes.add(builder);
    }
    public static void Recipe(String recipeName, Supplier<RecipeBuilder> builder) {
        recipes.add(builder);
        recipeNames.put(recipes.size() - 1, recipeName);
    }
    public static void SimpleShapedRecipe(String resultID, int count, String inputID, String[] pattern, RecipeCategory category, String group) {
        recipes.add(() -> {
            var input = Registry.getItem(inputID);
            var result = Registry.getItem(resultID);
            var recipe = ShapedRecipeBuilder.shaped(category, result, count)
                    .define('#', input)
                    .unlockedBy("has_item", conditionsFromItem(input));
            for (String row: pattern) recipe.pattern(row);
            if (!group.isEmpty()) recipe.group(group);
            return recipe;
        });
    }
    public static void ShapelessRecipe(String resultID, int count, Object[] inputs, RecipeCategory category, String group) {
        recipes.add(() -> {
            var result = Registry.getItem(resultID);
            var recipe = ShapelessRecipeBuilder.shapeless(category, result, count)
                    .unlockedBy("has_item", conditionsFromItem(getInput(inputs[0])));
            for (Object input: inputs) recipe.requires(getInput(input));
            if (!group.isEmpty()) recipe.group(group);
            return recipe;
        });
    }
    protected static ItemLike getInput(Object input) {
        if (input instanceof ItemLike item) return item;
        if (input instanceof String inputID) return Registry.getItem(inputID);
        return null;
    }

    public static class Recipes {
        public static void FloatingFlower(String ID) {
            recipes.add(() -> {
                var floatingFlower = Registry.getItem(CustomFlower.FLOATING(ID));
                var flower = Registry.getItem(ID);
                return ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, floatingFlower)
                        .requires(flower)
                        .requires(BotaniaTags.Items.FLOATING_FLOWERS)
                        .group("botania:floating_flower")
                        .unlockedBy("has_item", conditionsFromItem(flower));
            });
        }
    }

    private static class RecipeGenerator extends RecipeProvider implements IConditionBuilder {

        public RecipeGenerator(PackOutput packOutput) {
            super(packOutput);
        }

        @Override
        protected void buildRecipes(Consumer<FinishedRecipe> writer) {
            for (int i = 0; i < recipes.size(); i++) {
                var builder = recipes.get(i).get();
                if (!recipeNames.containsKey(i)) builder.save(writer);
                else builder.save(writer, WindowBox.RL(recipeNames.get(i)));
            }
        }
    }
    //endregion
}
