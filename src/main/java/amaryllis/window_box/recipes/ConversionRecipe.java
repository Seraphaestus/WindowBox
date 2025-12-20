package amaryllis.window_box.recipes;

import com.google.gson.JsonObject;
import net.minecraft.commands.CommandFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import vazkii.botania.api.block_entity.SpecialFlowerBlockEntity;
import vazkii.botania.api.recipe.StateIngredient;
import vazkii.botania.common.crafting.StateIngredientHelper;

import java.lang.reflect.InvocationTargetException;

public class ConversionRecipe implements Recipe<Container> {
    protected final ResourceLocation id;
    protected final StateIngredient input;
    protected final BlockState output;

    public ConversionRecipe(ResourceLocation id, StateIngredient input, BlockState output) {
        this.id = id;
        this.input = input;
        this.output = output;
    }

    @Override public ResourceLocation getId() { return id; }
    public StateIngredient getInput() { return input; }
    public BlockState getOutputState() { return output; }

    //region Override me!
    @Override public RecipeType<?> getType() { assert(false); return null; }
    @Override public RecipeSerializer<?> getSerializer() { assert(false); return null; }
    //endregion

    //region Boilerplate
    @Override public boolean isSpecial() { return true; }
    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, @NotNull RegistryAccess registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(@NotNull RegistryAccess registries) { return ItemStack.EMPTY; }
    //endregion

    public boolean matches(BlockState state) {
        return input.test(state) && state != output;
    }

    public boolean set(Level level, BlockPos pos) {
        if (level.isClientSide) return true;

        if (level.setBlockAndUpdate(pos, output)) {
            onSet(level, pos);
            return true;
        }
        return false;
    }
    public void onSet(Level level, BlockPos pos) {};


    public static class Serializer<T extends ConversionRecipe> implements RecipeSerializer<T> {
        protected Class<T> clazz;

        public Serializer(Class<T> clazz) {
            this.clazz = clazz;
        }

        protected T instanceRecipe(ResourceLocation id, StateIngredient input, BlockState output) {
            try {
                return clazz.getDeclaredConstructor().newInstance(id, input, output);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public @NotNull T fromJson(@NotNull ResourceLocation id, JsonObject object) {
            StateIngredient input = StateIngredientHelper.deserialize(GsonHelper.getAsJsonObject(object, "input"));
            BlockState output = StateIngredientHelper.readBlockState(GsonHelper.getAsJsonObject(object, "output"));
            return instanceRecipe(id, input, output);
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buf, T recipe) {
            recipe.input.write(buf);
            buf.writeVarInt(Block.getId(recipe.output));
        }

        @Override
        public @NotNull T fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buf) {
            StateIngredient input = StateIngredientHelper.read(buf);
            BlockState output = Block.stateById(buf.readVarInt());
            return instanceRecipe(id, input, output);
        }
    }
}
