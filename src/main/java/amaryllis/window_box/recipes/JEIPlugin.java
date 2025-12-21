package amaryllis.window_box.recipes;

import amaryllis.window_box.Registry;
import amaryllis.window_box.WindowBox;
import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.flower.functional.Groundberry;
import amaryllis.window_box.flower.functional.Oxidaisel;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import vazkii.botania.client.integration.jei.*;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@JeiPlugin
public class JEIPlugin implements IModPlugin {

    private static final ResourceLocation ID = WindowBox.RL("main");
    @Override public @NotNull ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        var guiHelper = registry.getJeiHelpers().getGuiHelper();
        registry.addRecipeCategories(
                new Groundberry.RecipeCategory(guiHelper),
                new Oxidaisel.RecipeCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registry) {
        registerFlowerCatalyst(registry, Groundberry.RecipeCategory.TYPE, Groundberry.ID);
        registerFlowerCatalyst(registry, Oxidaisel.RecipeCategory.TYPE, Oxidaisel.ID);
    }
    protected void registerFlowerCatalyst(IRecipeCatalystRegistration registry, mezz.jei.api.recipe.RecipeType<?> recipeType, String ID) {
        registry.addRecipeCatalyst(Registry.createStack(ID), recipeType);
        registry.addRecipeCatalyst(Registry.createStack(CustomFlower.FLOATING(ID)), recipeType);
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registry) {
        // Procedural Groundberry recipes from BrushableBlocks. Manually add these two so they sort to the top, in this order.
        List<Groundberry.Recipe> groundberryRecipes = List.of(
                Groundberry.Recipe.create((BrushableBlock) Blocks.SUSPICIOUS_SAND),
                Groundberry.Recipe.create((BrushableBlock) Blocks.SUSPICIOUS_GRAVEL)
        );
        ForgeRegistries.BLOCKS.getValues().stream()
                .filter(block -> block instanceof BrushableBlock && block != Blocks.SUSPICIOUS_SAND && block != Blocks.SUSPICIOUS_GRAVEL)
                .forEach(output -> groundberryRecipes.add(Groundberry.Recipe.create((BrushableBlock)output)));
        registry.addRecipes(Groundberry.RecipeCategory.TYPE, groundberryRecipes);

        // Oxidaisel recipes
        List<Oxidaisel.Recipe> oxidaiselRecipes = new ArrayList<>();
        ForgeRegistries.BLOCKS.getValues().stream()
                .filter(block -> Oxidaisel.WEATHERING_CONVERSIONS.containsKey(block))
                .forEach(block -> {
                    var recipe = Oxidaisel.Recipe.create(block);
                    if (recipe != null) oxidaiselRecipes.add(recipe);
                });
        oxidaiselRecipes.sort(JEIPlugin::sortMinecraftFirst);
        registry.addRecipes(Oxidaisel.RecipeCategory.TYPE, oxidaiselRecipes);
    }

    public static int sortMinecraftFirst(ConversionRecipe a, ConversionRecipe b) {
        var aRL = ForgeRegistries.BLOCKS.getKey(a.output.getBlock());
        var bRL = ForgeRegistries.BLOCKS.getKey(b.output.getBlock());
        boolean aIsMC = aRL.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE);
        boolean bIsMC = bRL.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE);
        if (aIsMC && !bIsMC) return -1;
        if (!aIsMC && bIsMC) return 1;
        return aRL.compareNamespaced(bRL);
    }
}
