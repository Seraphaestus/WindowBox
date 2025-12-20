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
        // Procedural Groundberry recipes from BrushableBlocks
        List<Groundberry.Recipe> groundberryRecipes = List.of(
                Groundberry.Recipe.create((BrushableBlock) Blocks.SUSPICIOUS_SAND),
                Groundberry.Recipe.create((BrushableBlock) Blocks.SUSPICIOUS_GRAVEL)
        );
        ForgeRegistries.BLOCKS.getValues().stream()
                .filter(block -> block instanceof BrushableBlock && block != Blocks.SUSPICIOUS_SAND && block != Blocks.SUSPICIOUS_GRAVEL)
                .forEach(output -> groundberryRecipes.add(Groundberry.Recipe.create((BrushableBlock)output)));
        registry.addRecipes(Groundberry.RecipeCategory.TYPE, groundberryRecipes);

        // Procedural Oxidaisel recipes from WeatheringCopper blocks
        List<Oxidaisel.Recipe> oxidaiselRecipes = new ArrayList<>();
        ForgeRegistries.BLOCKS.getValues().stream()
                .filter(block -> block instanceof WeatheringCopper)
                .forEach(block -> {
                    var recipe = Oxidaisel.Recipe.create(block);
                    if (recipe != null) oxidaiselRecipes.add(recipe);
                });
        registry.addRecipes(Oxidaisel.RecipeCategory.TYPE, oxidaiselRecipes);
    }

}
