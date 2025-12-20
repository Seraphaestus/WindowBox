package amaryllis.window_box.tree;

import amaryllis.window_box.DataGen;
import amaryllis.window_box.Registry;
import amaryllis.window_box.WindowBox;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.function.Supplier;

import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.Registry.RegisterBlock;
import static vazkii.botania.data.recipes.CraftingRecipeProvider.conditionsFromItem;
import static vazkii.botania.data.recipes.CraftingRecipeProvider.conditionsFromTag;

public class TreeHelper {

    public static final ArrayList<WoodType> WOOD_TYPES = new ArrayList<>();

    public static BlockSetType RegisterWoodSetType(String ID) {
        return BlockSetType.register(new BlockSetType(ID, true, SoundType.WOOD, SoundEvents.WOODEN_DOOR_CLOSE, SoundEvents.WOODEN_DOOR_OPEN, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF, SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON, SoundEvents.WOODEN_BUTTON_CLICK_OFF, SoundEvents.WOODEN_BUTTON_CLICK_ON));
    }
    public static WoodType RegisterWoodType(String ID, BlockSetType setType) {
        var woodType = new WoodType(ID, setType);
        WOOD_TYPES.add(woodType);
        WoodType.register(woodType);
        return woodType;
    }

    public static void register(String ID, BlockSetType setType, WoodType woodType, Supplier<? extends Block> sapling, Supplier<? extends Block> leaves, MapColor barkColor, MapColor strippedColor, MapColor planksColor) {
        RegisterBlockOnly(ID + "_sapling", sapling);
        RegisterItem(ID + "_sapling", () -> new SpecialSapling.Item(getBlock(ID + "_sapling")));
        DataGen.BlockModel(ID + "_sapling", (models, name) -> DataGen.Models.cross(models, name, ID + "/sapling"));
        DataGen.ItemModel(ID + "_sapling", (models, name) -> DataGen.Models.basicItem(models, name, WindowBox.RL("amaryllis/window_box/block/" + ID + "/sapling")));
        DataGen.TagBoth(ID + "_sapling", BlockTags.SAPLINGS, ItemTags.SAPLINGS);
        DataGen.TagBlock(ID + "_sapling", BlockTags.MINEABLE_WITH_AXE);

        RegisterBlock(ID + "_leaves", leaves);
        DataGen.BlockModel(ID + "_leaves", (models, name) -> DataGen.Models.leaves(models, name, ID + "/leaves"));
        DataGen.TagBoth(ID + "_leaves", BlockTags.LEAVES, ItemTags.LEAVES);
        DataGen.TagBlock(ID + "_leaves", BlockTags.MINEABLE_WITH_HOE);
        RegisterFlammable(FlammableType.LEAVES, ID + "_leaves");

        var logsTagBlock = TagKey.create(Registries.BLOCK, WindowBox.RL(ID + "_logs"));
        var logsTagItem = TagKey.create(Registries.ITEM, WindowBox.RL(ID + "_logs"));
        LogSet(ID, barkColor, strippedColor, logsTagBlock, logsTagItem);
        WoodSet(ID, setType, woodType, planksColor, logsTagItem);
    }

    public static void LogSet(String ID, MapColor barkColor, MapColor strippedColor, TagKey<Block> blockTag, TagKey<Item> itemTag) {
        RegisterBlock(ID + "_log",
                () -> new RotatedPillarBlock(propOf(Blocks.OAK_LOG).mapColor(state -> (state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y) ? strippedColor : barkColor)));
        RegisterBlock(ID + "_wood",
                () -> new RotatedPillarBlock(propOf(Blocks.OAK_LOG).mapColor(barkColor)));
        RegisterBlock("stripped_" + ID + "_log",
                () -> new RotatedPillarBlock(propOf(Blocks.OAK_LOG).mapColor(strippedColor)));
        RegisterBlock("stripped_" + ID + "_wood",
                () -> new RotatedPillarBlock(propOf(Blocks.OAK_LOG).mapColor(strippedColor)));
        RegisterBlock(ID + "_wood_stairs", () -> new NaturalStairs(getBlock(ID + "_wood")::defaultBlockState, propOf(getBlock(ID + "_wood"))));
        RegisterBlock(ID + "_wood" + "_slab", () -> new NaturalSlab(propOf(getBlock(ID + "_wood"))));

        RegisterAxeStripping(ID + "_log");
        RegisterAxeStripping(ID + "_wood");
        RegisterFlammable(FlammableType.LOG, ID + "_log", ID + "_wood", "stripped_" + ID + "_log", "stripped_" + ID + "_wood");

        DataGen.BlockVariantsModel(ID + "_log", DataGen.BlockVariantTypes.LOG, ID + "/log");
        DataGen.BlockVariantsModel(ID + "_wood", DataGen.BlockVariantTypes.WOOD, ID + "/log");
        DataGen.BlockVariantsModel(ID + "_wood_stairs", DataGen.BlockVariantTypes.STAIRS, ID + "/log");
        DataGen.BlockVariantsModel(ID + "_wood_slab", DataGen.BlockVariantTypes.SLAB, ID + "/log", ID + "_wood");
        DataGen.BlockVariantsModel("stripped_" + ID + "_log", DataGen.BlockVariantTypes.LOG, ID + "/stripped_log");
        DataGen.BlockVariantsModel("stripped_" + ID + "_wood", DataGen.BlockVariantTypes.WOOD, ID + "/stripped_log");

        TagLog(ID + "_log", blockTag, itemTag);
        TagLog(ID + "_wood", blockTag, itemTag);
        TagLog("stripped_" + ID + "_log", blockTag, itemTag);
        TagLog("stripped_" + ID + "_wood", blockTag, itemTag);
        TagStairs(ID + "_wood_stairs");
        TagSlab(ID + "_wood_slab");

        DataGen.SimpleShapedRecipe(ID + "_wood", 3, ID + "_log", new String[]{"##", "##"}, RecipeCategory.BUILDING_BLOCKS, "bark");
        DataGen.SimpleShapedRecipe("stripped_" + ID + "_wood", 3, "stripped_" + ID + "_log", new String[]{"##", "##"}, RecipeCategory.BUILDING_BLOCKS, "bark");
        DataGen.SimpleShapedRecipe( ID + "_wood_stairs", 4,  ID + "_wood", new String[]{"#  ", "## ", "###"}, RecipeCategory.BUILDING_BLOCKS, "");
        DataGen.SimpleShapedRecipe( ID + "_wood_slab", 6,  ID + "_wood", new String[]{"###"}, RecipeCategory.BUILDING_BLOCKS, "");
    }

    public static void WoodSet(String ID, BlockSetType setType, WoodType woodType, MapColor color, TagKey<Item> logsTag) {
        RegisterBlock(ID + "_planks", () -> new Block(propOf(Blocks.OAK_PLANKS).mapColor(color)));
        RegisterStairsAndSlab(ID, ID + "_planks");
        RegisterBlock(ID + "_fence", () -> new FenceBlock(propOf(Blocks.OAK_FENCE).mapColor(color)));
        RegisterBlock(ID + "_fence_gate", () -> new FenceGateBlock(propOf(Blocks.OAK_FENCE_GATE).mapColor(color), woodType));
        RegisterBlock(ID + "_door", () -> new DoorBlock(propOf(Blocks.OAK_DOOR).mapColor(color), setType));
        RegisterBlock(ID + "_trapdoor", () -> new TrapDoorBlock(propOf(Blocks.OAK_TRAPDOOR).mapColor(color), setType));
        RegisterBlock(ID + "_button", () -> new ButtonBlock(propOf(Blocks.OAK_BUTTON).mapColor(color), setType, 30, true));
        RegisterBlock(ID + "_pressure_plate", () -> new PressurePlateBlock(PressurePlateBlock.Sensitivity.EVERYTHING, propOf(Blocks.OAK_PRESSURE_PLATE).mapColor(color), setType));
        CustomSigns.RegisterVariant(ID, woodType);
        CustomBoat.RegisterVariant(ID);

        RegisterFlammable(FlammableType.BLOCK, ID + "_planks", ID + "_stairs", ID + "_slab", ID + "_fence", ID + "_fence_gate");

        DataGen.BlockModel(ID + "_planks", (models, name) -> DataGen.Models.block(models, name, ID + "/planks"));
        DataGen.BlockVariantsModel(ID + "_stairs", DataGen.BlockVariantTypes.STAIRS, ID + "/planks");
        DataGen.BlockVariantsModel(ID + "_slab", DataGen.BlockVariantTypes.SLAB, ID + "/planks", ID + "_planks");
        DataGen.BlockVariantsModel(ID + "_fence", DataGen.BlockVariantTypes.FENCE, ID + "/planks");
        DataGen.BlockVariantsModel(ID + "_fence_gate", DataGen.BlockVariantTypes.FENCE_GATE, ID + "/planks");
        DataGen.BlockVariantsModel(ID + "_door", DataGen.BlockVariantTypes.DOOR, ID + "/door");
        DataGen.BlockVariantsModel(ID + "_trapdoor", DataGen.BlockVariantTypes.TRAPDOOR, ID + "/trapdoor");
        DataGen.BlockVariantsModel(ID + "_button", DataGen.BlockVariantTypes.BUTTON, ID + "/planks");
        DataGen.BlockVariantsModel(ID + "_pressure_plate", DataGen.BlockVariantTypes.PRESSURE_PLATE, ID + "/planks");
        DataGen.BlockVariantsModel(ID + "_sign", DataGen.BlockVariantTypes.SIGN, ID + "/planks", ID + "_wall_sign");
        DataGen.BlockVariantsModel(ID + "_hanging_sign", DataGen.BlockVariantTypes.HANGING_SIGN, ID + "/planks", ID + "_wall_hanging_sign");
        DataGen.ItemModel(ID + "_boat");
        DataGen.ItemModel(ID + "_chest_boat");

        TagPlanks(ID + "_planks");
        TagStairs(ID + "_stairs");
        TagSlab(ID + "_slab");
        TagFence(ID + "_fence");
        TagFenceGate(ID + "_fence_gate");
        TagDoor(ID + "_door");
        TagTrapdoor(ID + "_trapdoor");
        TagButton(ID + "_button");
        TagPressurePlate(ID + "_pressure_plate");
        TagSign(ID);
        TagHangingSign(ID);
        TagBoats(ID);

                Supplier<Item> planks = () -> Registry.getItem(ID + "_planks");
                Supplier<Item> strippedLog = () -> Registry.getItem("stripped_" + ID + "_log");
                DataGen.Recipe(() -> ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, planks.get(), 4)
                            .requires(logsTag)
                            .group("planks")
                            .unlockedBy("has_item", conditionsFromTag(logsTag)));
        DataGen.SimpleShapedRecipe( ID + "_stairs", 4,  ID + "_planks", new String[]{"#  ", "## ", "###"}, RecipeCategory.BUILDING_BLOCKS, "wooden_stairs");
        DataGen.SimpleShapedRecipe( ID + "_slab", 6,  ID + "_planks", new String[]{"###"}, RecipeCategory.BUILDING_BLOCKS, "wooden_slab");
        DataGen.Recipe(() -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Registry.getItem(ID + "_fence"), 3)
                .define('S', Items.STICK)
                .define('P', planks.get())
                .pattern("PSP").pattern("PSP")
                .group("wooden_fence")
                .unlockedBy("has_item", conditionsFromItem(planks.get())));
        DataGen.Recipe(() -> ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, Registry.getItem(ID + "_fence_gate"))
                .define('S', Items.STICK)
                .define('P', planks.get())
                .pattern("SPS").pattern("SPS")
                .group("wooden_fence_gate")
                .unlockedBy("has_item", conditionsFromItem(planks.get())));
        DataGen.SimpleShapedRecipe( ID + "_door", 3,  ID + "_planks", new String[]{"##", "##", "##"}, RecipeCategory.REDSTONE, "wooden_door");
        DataGen.SimpleShapedRecipe( ID + "_trapdoor", 2,  ID + "_planks", new String[]{"###", "###"}, RecipeCategory.REDSTONE, "wooden_trapdoor");
        DataGen.ShapelessRecipe(ID + "_button", 1, new String[]{ID + "_planks"}, RecipeCategory.REDSTONE, "wooden_button");
        DataGen.SimpleShapedRecipe( ID + "_pressure_plate", 1,  ID + "_planks", new String[]{"##"}, RecipeCategory.REDSTONE, "wooden_pressure_plate");
        DataGen.Recipe(() -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Registry.getItem(ID + "_sign"), 3)
                .define('S', Items.STICK)
                .define('P', planks.get())
                .pattern("PPP").pattern("PPP").pattern(" S ")
                .group("wooden_sign")
                .unlockedBy("has_item", conditionsFromItem(planks.get())));
        DataGen.Recipe(() -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Registry.getItem(ID + "_hanging_sign"), 6)
                .define('X', Blocks.CHAIN)
                .define('L', strippedLog.get())
                .pattern("X X").pattern("LLL").pattern("LLL")
                .group("hanging_sign")
                .unlockedBy("has_item", conditionsFromItem(strippedLog.get())));
        DataGen.SimpleShapedRecipe( ID + "_boat", 1,  ID + "_planks", new String[]{"# #", "###"}, RecipeCategory.MISC, "boat");
        Supplier<Item> boat = () -> Registry.getItem(ID + "_boat");
        DataGen.Recipe(() -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Registry.getItem(ID + "_chest_boat"))
                .requires(Blocks.CHEST)
                .requires(boat.get())
                .group("chest_boat")
                .unlockedBy("has_item", conditionsFromItem(boat.get())));

        DataGen.Recipe(ID + "_planks_from_slabs", () -> ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, planks.get())
                .requires(Registry.getItem(ID + "_slab"), 2)
                .unlockedBy("has_item", conditionsFromItem(Registry.getItem(ID + "_slab"))));
    }


    protected static void TagLog(String ID, TagKey<Block> blockTag, TagKey<Item> itemTag) {
        DataGen.TagBoth(ID, blockTag, itemTag);
        DataGen.TagBoth(ID, BlockTags.LOGS_THAT_BURN, ItemTags.LOGS_THAT_BURN);
        DataGen.TagBlock(ID, BlockTags.MINEABLE_WITH_AXE);
    }
    protected static void TagPlanks(String ID) {
        DataGen.TagBoth(ID, BlockTags.PLANKS, ItemTags.PLANKS);
        DataGen.TagBlock(ID, BlockTags.MINEABLE_WITH_AXE);
    }
    protected static void TagStairs(String ID) {
        DataGen.TagBoth(ID, BlockTags.WOODEN_STAIRS, ItemTags.WOODEN_STAIRS);
        DataGen.TagBlock(ID, BlockTags.MINEABLE_WITH_AXE);
    }
    protected static void TagSlab(String ID) {
        DataGen.TagBoth(ID, BlockTags.WOODEN_SLABS, ItemTags.WOODEN_SLABS);
        DataGen.TagBlock(ID, BlockTags.MINEABLE_WITH_AXE);
    }
    protected static void TagFence(String ID) {
        DataGen.TagBoth(ID, BlockTags.WOODEN_FENCES, ItemTags.WOODEN_FENCES);
        DataGen.TagBlock(ID, BlockTags.MINEABLE_WITH_AXE);
    }
    protected static void TagFenceGate(String ID) {
        DataGen.TagBoth(ID, BlockTags.FENCE_GATES, ItemTags.FENCE_GATES);
        DataGen.TagBlock(ID, BlockTags.MINEABLE_WITH_AXE);
    }
    protected static void TagDoor(String ID) {
        DataGen.TagBoth(ID, BlockTags.WOODEN_DOORS, ItemTags.WOODEN_DOORS);
        DataGen.TagBlock(ID, BlockTags.MINEABLE_WITH_AXE);
    }
    protected static void TagTrapdoor(String ID) {
        DataGen.TagBoth(ID, BlockTags.WOODEN_TRAPDOORS, ItemTags.WOODEN_TRAPDOORS);
        DataGen.TagBlock(ID, BlockTags.MINEABLE_WITH_AXE);
    }
    protected static void TagButton(String ID) {
        DataGen.TagBoth(ID, BlockTags.WOODEN_BUTTONS, ItemTags.WOODEN_BUTTONS);
        DataGen.TagBlock(ID, BlockTags.MINEABLE_WITH_AXE);
    }
    protected static void TagPressurePlate(String ID) {
        DataGen.TagBoth(ID, BlockTags.WOODEN_PRESSURE_PLATES, ItemTags.WOODEN_PRESSURE_PLATES);
        DataGen.TagBlock(ID, BlockTags.MINEABLE_WITH_AXE);
    }
    protected static void TagSign(String ID) {
        DataGen.TagBoth(ID + "_sign", BlockTags.STANDING_SIGNS, ItemTags.SIGNS);
        DataGen.TagBlock(ID + "_wall_sign", BlockTags.WALL_SIGNS);
        DataGen.TagBlock(ID + "_sign", BlockTags.MINEABLE_WITH_AXE);
        DataGen.TagBlock(ID + "_wall_sign", BlockTags.MINEABLE_WITH_AXE);
    }
    protected static void TagHangingSign(String ID) {
        DataGen.TagBoth(ID + "_hanging_sign", BlockTags.CEILING_HANGING_SIGNS, ItemTags.HANGING_SIGNS);
        DataGen.TagBlock(ID + "_wall_hanging_sign", BlockTags.WALL_HANGING_SIGNS);
        DataGen.TagBlock(ID + "_hanging_sign", BlockTags.MINEABLE_WITH_AXE);
        DataGen.TagBlock(ID + "_wall_hanging_sign", BlockTags.MINEABLE_WITH_AXE);
    }
    protected static void TagBoats(String ID) {
        DataGen.TagItem(ID + "_boat", ItemTags.BOATS);
        DataGen.TagItem(ID + "_chest_boat", ItemTags.CHEST_BOATS);
    }

}
