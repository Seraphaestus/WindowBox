package amaryllis.window_box;

import amaryllis.window_box.entity.FakePlayer;
import amaryllis.window_box.entity.LivingMarker;
import amaryllis.window_box.flower.functional.*;
import amaryllis.window_box.flower.generating.Candysnuft;
import amaryllis.window_box.flower.generating.QueenAnelace;
import amaryllis.window_box.item.Bouquet;
import amaryllis.window_box.item.GrassRod;
import amaryllis.window_box.item.Potions;
import amaryllis.window_box.item.StateChangerRod;
import amaryllis.window_box.patchouli.OutlineBlock;
import amaryllis.window_box.tree.*;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.BotaniaRegistries;
import vazkii.botania.api.block.WandHUD;
import vazkii.botania.common.block.decor.stairs.BotaniaStairBlock;
import vazkii.botania.forge.CapabilityUtil;
import vazkii.botania.mixin.FireBlockAccessor;
import vazkii.botania.xplat.XplatAbstractions;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static amaryllis.window_box.WindowBox.MOD_ID;
import static amaryllis.window_box.WindowBox.RL;
import static net.minecraft.core.Registry.register;

@Mod.EventBusSubscriber(modid = WindowBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Registry {
    private static final DeferredRegister<Block> BLOCKS_REGISTER = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final HashMap<String, RegistryObject<Block>> BLOCKS = new HashMap<>();

    private static final DeferredRegister<Item> ITEMS_REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final HashMap<String, RegistryObject<Item>> ITEMS = new HashMap<>();

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES_REGISTER = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    private static final HashMap<String, RegistryObject<BlockEntityType<?>>> BLOCK_ENTITY_TYPES = new HashMap<>();
    public static final ArrayList<Consumer<EntityRenderersEvent.RegisterRenderers>> BE_RENDERER_REGISTRARS = new ArrayList<>();

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);

    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES_REGISTER = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID);
    public static final HashMap<String, Tuple<RegistryObject<ParticleType<? extends ParticleOptions>>, ParticleEngine.SpriteParticleRegistration<SimpleParticleType>>> PARTICLE_TYPES = new HashMap<>();

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);

    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(ForgeRegistries.POTIONS, MOD_ID);

    public enum FlammableType { LOG, LEAVES, BLOCK }
    public static final HashMap<String, FlammableType> FLAMMABILITY = new HashMap<>();

    public static final HashMap<String, String> AXE_STRIPPING = new HashMap<>();

    public static final HashMap<String, Function<BlockEntity, WandHUD>> BE_WAND_CAPABILITIES = new HashMap<>();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    private static final ArrayList<String> ORDERED_ITEMS = new ArrayList<>();

    public static final ArrayList<String> MANUAL_BLOCK_LOOT = new ArrayList<>();

    public static class ModRecipeType<T extends Recipe<?>> implements RecipeType<T> {
        @Override
        public String toString() {
            return ForgeRegistries.RECIPE_TYPES.getKey(this).toString();
        }
    }

    public static void init(IEventBus bus) {
        // Init registries
        BLOCKS_REGISTER.register(bus);
        ITEMS_REGISTER.register(bus);
        BLOCK_ENTITY_TYPES_REGISTER.register(bus);
        ENTITY_TYPES.register(bus);
        PARTICLE_TYPES_REGISTER.register(bus);
        SOUNDS.register(bus);
        POTIONS.register(bus);
        CREATIVE_MODE_TABS.register(bus);

        // Entities
        LivingMarker.register();
        FakePlayer.register();
        CustomBoat.register();
        // Items
        Bouquet.register();
        GrassRod.register();
        StateChangerRod.register();
        // Misc Flowers
        Snapdresson.register();
        // Generating Flowers
        Candysnuft.register();
        // TODO: if (hasAnyMod("copperandtuffbackport", "copperagebackport")) Darkspur.register();
        QueenAnelace.register();
        // Functional Flowers
        Groundberry.register();
        Oxidaisel.register();
        GloryIncarnata.register();
        WitchPupil.register();
        Dispelagonium.register();
        if (hasMod("gravityapi")) TopsyTulip.register();
        // Trees
        ChthonicYew.register();
        Alfthorne.register();
        CustomSigns.register();
        // Misc
        OutlineBlock.register();
        Potions.register();

        // Register Creative Tab
        CREATIVE_MODE_TABS.register(MOD_ID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MOD_ID))
            .withTabsBefore(BotaniaRegistries.BOTANIA_TAB_KEY)
            .icon(() -> getItem(Snapdresson.ID).getDefaultInstance())
            .displayItems((parameters, output) ->
                    ORDERED_ITEMS.forEach(ID -> {
                        var item = getItem(ID);
                        if (item instanceof DefaultCreativeStack _item) output.accept(_item.createDefaultStack());
                        else output.accept(item);
                    })
            ).build());
    }

    protected static boolean hasMod(String dependency) {
        return ModList.get().isLoaded(dependency);
    }
    protected static boolean hasAnyMod(String... dependencies) {
        return Arrays.stream(dependencies).anyMatch(Registry::hasMod);
    }

    public static void onCommonSetup() {
        Groundberry.cacheRecipes();

        Potions.registerRecipes();

        SpecialSapling.GrowProcessor.TYPE = register(BuiltInRegistries.STRUCTURE_PROCESSOR,
                "grow_sapling", () -> SpecialSapling.GrowProcessor.CODEC);

        AXE_STRIPPING.forEach((input, output) -> XplatAbstractions.INSTANCE.addAxeStripping(Registry.getBlock(input), Registry.getBlock(output)));

        FireBlockAccessor fireAccessor = (FireBlockAccessor) Blocks.FIRE;
        FLAMMABILITY.forEach((ID, type) -> {
            switch(type) {
                case LOG -> fireAccessor.botania_register(Registry.getBlock(ID), 5, 5);
                case LEAVES -> fireAccessor.botania_register(Registry.getBlock(ID), 30, 60);
                default -> fireAccessor.botania_register(Registry.getBlock(ID), 5, 20);
            }
        });
    }

    @SubscribeEvent
    public static void attachItemCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        var stack = event.getObject();
        if (stack.getItem().equals(getItem(GrassRod.ID))) {
            event.addCapability(RL("botania", "avatar_wieldable"),
                    CapabilityUtil.makeProvider(BotaniaForgeCapabilities.AVATAR_WIELDABLE, new GrassRod.AvatarBehavior()));
            event.addCapability(RL("botania", "block_provider"),
                    CapabilityUtil.makeProvider(BotaniaForgeCapabilities.BLOCK_PROVIDER, new GrassRod.GrassBlockProvider(stack)));
        }
    }


    public static void RegisterBlockOnly(String ID, Supplier<? extends Block> block) {
        BLOCKS.put(ID, BLOCKS_REGISTER.register(ID, block));
    }
    public static void RegisterItem(String ID, Supplier<? extends Item> item) {
        ITEMS.put(ID, ITEMS_REGISTER.register(ID, item));
        ORDERED_ITEMS.add(ID);
    }
    public static void RegisterBlock(String ID, Supplier<? extends Block> block) {
        RegisterBlockOnly(ID, block);
        RegisterItem(ID, () -> new BlockItem(getBlock(ID), new Item.Properties()));
    }
    public static void RegisterBlockEntityType(String ID, BlockEntityType.BlockEntitySupplier<? extends BlockEntity> constructor) {
        BLOCK_ENTITY_TYPES.put(ID, BLOCK_ENTITY_TYPES_REGISTER.register(ID, () ->
                BlockEntityType.Builder.of(constructor, getBlock(ID)).build(null)));
    }
    public static void RegisterBlockEntityType(String ID, BlockEntityType.BlockEntitySupplier<? extends BlockEntity> constructor, List<String> blockIDs) {
        BLOCK_ENTITY_TYPES.put(ID, BLOCK_ENTITY_TYPES_REGISTER.register(ID, () -> {
            Block[] blocks = new Block[blockIDs.size()];
            for (int i = 0; i < blockIDs.size(); i++) blocks[i] = getBlock(blockIDs.get(i));
            return BlockEntityType.Builder.of(constructor, blocks).build(null);
        }));
    }

    public static <T extends BlockEntity> void RegisterBlockEntityRenderer(String ID, BlockEntityRendererProvider<T> renderer) {
        BE_RENDERER_REGISTRARS.add(event -> event.registerBlockEntityRenderer((BlockEntityType<T>)getBlockEntityType(ID), renderer));
    }

    public static void RegisterParticleType(String ID, ParticleEngine.SpriteParticleRegistration<SimpleParticleType> factory) {
        PARTICLE_TYPES.put(ID, new Tuple<>(
                PARTICLE_TYPES_REGISTER.register(ID, () -> new SimpleParticleType(false)),
                factory));
    }

    public static RegistryObject<SoundEvent> RegisterSound(String ID) {
        return SOUNDS.register(ID, () -> SoundEvent.createVariableRangeEvent(WindowBox.RL(ID)));
    }

    public static void RegisterAxeStripping(String ID) {
        AXE_STRIPPING.put(ID, "stripped_" + ID);
    }

    public static void RegisterFlammable(FlammableType type, String... IDs) {
        for (String ID: IDs) FLAMMABILITY.put(ID, type);
    }

    public static void RegisterWandHUD(String ID, Function<BlockEntity, WandHUD> wandHUD) {
        BE_WAND_CAPABILITIES.put(ID, wandHUD);
    }

    public static Block getBlock(String ID) { return BLOCKS.get(ID).get(); }
    public static Item getItem(String ID) { return ITEMS.get(ID).get(); }
    public static BlockEntityType<? extends BlockEntity> getBlockEntityType(String ID) { return BLOCK_ENTITY_TYPES.get(ID).get(); }
    public static SimpleParticleType getParticleType(String ID) { return (SimpleParticleType)PARTICLE_TYPES.get(ID).getA().get(); }

    public static Stream<Block> getBlocks() { return BLOCKS.values().stream().map(RegistryObject::get); }
    public static Stream<Block> getBlocksForLootGen() {
        return BLOCKS.keySet().stream().filter(ID -> !MANUAL_BLOCK_LOOT.contains(ID)).map(Registry::getBlock);
    }


    public static ItemStack createStack(String ID) { return new ItemStack(getItem(ID)); }

    public static void RegisterStairsAndSlab(String ID, String baseID) {
        RegisterBlock(ID + "_stairs", () -> {
            var base = getBlock(baseID);
            return new BotaniaStairBlock(base.defaultBlockState(), propOf(base));
        });
        RegisterBlock(ID + "_slab", () -> new SlabBlock(propOf(getBlock(baseID))));
    }

    public static BlockBehaviour.Properties propOf(Block block) {
        return BlockBehaviour.Properties.copy(block);
    }


    public interface DefaultCreativeStack {
        ItemStack createDefaultStack();
    }
}
