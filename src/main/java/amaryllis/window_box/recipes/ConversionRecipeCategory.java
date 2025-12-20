package amaryllis.window_box.recipes;

import amaryllis.window_box.Registry;
import amaryllis.window_box.WindowBox;
import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import vazkii.botania.api.recipe.StateIngredient;
import vazkii.botania.common.lib.LibMisc;

import static vazkii.botania.common.lib.ResourceLocationHelper.prefix;

public class ConversionRecipeCategory<T extends ConversionRecipe> implements IRecipeCategory<T> {

    protected final IDrawable background;
    protected final Component localizedName;
    protected final IDrawable overlay;
    protected final ItemStack catalystStack;
    protected final IDrawable icon;

    public ConversionRecipeCategory(IGuiHelper guiHelper, String ID) {
        background = guiHelper.createBlankDrawable(96, 44);
        localizedName = Component.translatable(WindowBox.MOD_ID + ".recipe_viewer." + ID);
        overlay = guiHelper.createDrawable(prefix("textures/gui/pure_daisy_overlay.png"),
                0, 0, 64, 44);
        catalystStack = Registry.createStack(ID);
        icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, catalystStack);
    }

    //region Override me!!
    @Override public @NotNull RecipeType<T> getRecipeType() { assert(false); return new RecipeType(WindowBox.RL(""), Object.class); }
    //endregion

    protected static <T extends ConversionRecipe> RecipeType<T> createRecipeType(String ID, Class<T> clazz) { return mezz.jei.api.recipe.RecipeType.create(WindowBox.MOD_ID, ID, clazz); }

    @Override public @NotNull Component getTitle() { return localizedName; }
    @Override public @NotNull IDrawable getBackground() { return background; }
    @Override public @NotNull IDrawable getIcon() { return icon; }

    @Override
    public void draw(T recipe, IRecipeSlotsView slotsView, GuiGraphics gui, double mouseX, double mouseY) {
        RenderSystem.enableBlend();
        overlay.draw(gui, 17, 0);
        RenderSystem.disableBlend();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull T recipe, @NotNull IFocusGroup focusGroup) {
        StateIngredient input = recipe.getInput();

        IRecipeSlotBuilder inputSlotBuilder = builder.addSlot(RecipeIngredientRole.INPUT, 9, 12);
        inputSlotBuilder.addItemStacks(input.getDisplayedStacks())
                .addTooltipCallback((view, tooltip) -> tooltip.addAll(input.descriptionTooltip()));

        builder.addSlot(RecipeIngredientRole.CATALYST, 39, 12).addItemStack(catalystStack);

        Block outBlock = recipe.getOutputState().getBlock();
        if (outBlock.asItem() != Items.AIR) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 68, 12).addItemStack(new ItemStack(outBlock));
        }
    }
}
