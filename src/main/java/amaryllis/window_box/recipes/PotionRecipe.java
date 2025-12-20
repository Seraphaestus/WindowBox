package amaryllis.window_box.recipes;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.common.brewing.IBrewingRecipe;

public record PotionRecipe(Potion requiredPotion, Item reagent, Potion result) implements IBrewingRecipe {

    @Override
    public boolean isInput(ItemStack input) {
        Item inputItem = input.getItem();
        return (inputItem == Items.POTION || inputItem == Items.SPLASH_POTION || inputItem == Items.LINGERING_POTION)
                && PotionUtils.getPotion(input) == this.requiredPotion;
    }

    @Override
    public boolean isIngredient(ItemStack ingredient) {
        return ingredient.is(reagent);
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        return isInput(input) && isIngredient(ingredient)
                ? PotionUtils.setPotion(new ItemStack(Items.POTION), result)
                : ItemStack.EMPTY;
    }
}
