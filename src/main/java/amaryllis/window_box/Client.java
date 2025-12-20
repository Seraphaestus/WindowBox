package amaryllis.window_box;

import amaryllis.window_box.entity.FakePlayer;
import amaryllis.window_box.entity.FakePlayerRenderer;
import amaryllis.window_box.entity.LivingMarker;
import amaryllis.window_box.flower.ManastarWandHUD;
import amaryllis.window_box.flower.generating.QueenAnelace;
import amaryllis.window_box.item.Bouquet;
import amaryllis.window_box.patchouli.BetterMultiblockPage;
import amaryllis.window_box.tree.CustomBoat;
import amaryllis.window_box.tree.TreeHelper;
import com.google.common.base.Suppliers;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.ChestBoatModel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import vazkii.botania.api.BotaniaForgeClientCapabilities;
import vazkii.botania.api.block.WandHUD;
import vazkii.botania.api.block_entity.BindableSpecialFlowerBlockEntity;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.GeneratingFlowerBlockEntity;
import vazkii.botania.common.block.BotaniaFlowerBlocks;
import vazkii.botania.common.block.flower.ManastarBlockEntity;
import vazkii.botania.common.lib.ResourceLocationHelper;
import vazkii.botania.forge.CapabilityUtil;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.minecraft.client.renderer.Sheets.SIGN_SHEET;

@Mod.EventBusSubscriber(modid = WindowBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class Client {

    @SubscribeEvent
    public static void clientInit(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, Client::AttachBlockEntityCapabilities);

        TreeHelper.WOOD_TYPES.forEach(woodType -> {
            Sheets.SIGN_MATERIALS.put(woodType, new Material(SIGN_SHEET, WindowBox.RL("entity/signs/" + woodType.name())));
            Sheets.HANGING_SIGN_MATERIALS.put(woodType, new Material(SIGN_SHEET, WindowBox.RL("entity/signs/hanging/" + woodType.name())));
        });

        EntityRenderers.register(LivingMarker.ENTITY_TYPE.get(), NoopRenderer::new);
        EntityRenderers.register(FakePlayer.ENTITY_TYPE.get(), FakePlayerRenderer::new);
        EntityRenderers.register(CustomBoat.ENTITY_TYPE.get(), context -> new CustomBoat.Renderer(context, false));
        EntityRenderers.register(CustomBoat.WithChest.ENTITY_TYPE.get(), context -> new CustomBoat.Renderer(context, true));

        BetterMultiblockPage.register();
    }

    @SubscribeEvent
    public static void RegisterBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        Registry.BE_RENDERER_REGISTRARS.forEach(registrar -> registrar.accept(event));
    }

    @SubscribeEvent
    public static void RegisterEntityLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        CustomBoat.ALL_VARIANTS.forEach(ID -> {
            event.registerLayerDefinition(CustomBoat.Renderer.createBoatModelName(ID), BoatModel::createBodyModel);
            event.registerLayerDefinition(CustomBoat.Renderer.createChestBoatModelName(ID), ChestBoatModel::createBodyModel);
        });
    }

    @SubscribeEvent
    public static void RegisterSpectatorShaders(RegisterEntitySpectatorShadersEvent event) {
        event.register(LivingMarker.ENTITY_TYPE.get(), WindowBox.RL("shaders/post/witch_pupil.json"));
    }

    @SubscribeEvent
    public static void AssignColorMapsToBlocks(RegisterColorHandlersEvent.Block event) {
        event.register(Client::grassColor, Registry.getBlock(QueenAnelace.SUSPICIOUS_GRASS_ID));
    }
    protected static int grassColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        return (level != null && pos != null) ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.getDefaultColor();
    }

    @SubscribeEvent
    public static void RegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(Bouquet.HOLDER);
    }

    @SubscribeEvent
    public static void RegisterParticles(RegisterParticleProvidersEvent event) {
        Registry.PARTICLE_TYPES.forEach((ID, data) ->
            event.registerSpriteSet((ParticleType<SimpleParticleType>)data.getA().get(), data.getB())
        );
    }

   //region Wand HUD
    private static void AttachBlockEntityCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        var blockEntity = event.getObject();

        var makeWandHud = WAND_HUD.get().get(blockEntity.getType());
        if (makeWandHud == null) return;

        event.addCapability(ResourceLocationHelper.prefix("wand_hud"),
                CapabilityUtil.makeProvider(BotaniaForgeClientCapabilities.WAND_HUD, makeWandHud.apply(blockEntity)));
    }

    public static final Function<BlockEntity, WandHUD> GENERATING_FLOWER_HUD = (be) -> new BindableSpecialFlowerBlockEntity.BindableFlowerWandHud<>((GeneratingFlowerBlockEntity) be);
    public static final Function<BlockEntity, WandHUD> FUNCTIONAL_FLOWER_HUD = (be) -> new BindableSpecialFlowerBlockEntity.BindableFlowerWandHud<>((FunctionalFlowerBlockEntity) be);

    private static final Supplier<Map<BlockEntityType<?>, Function<BlockEntity, WandHUD>>> WAND_HUD = Suppliers.memoize(() -> {
        var map = new IdentityHashMap<BlockEntityType<?>, Function<BlockEntity, WandHUD>>();

        Registry.BE_WAND_CAPABILITIES.forEach((ID, wandHUD) ->
                map.put(Registry.getBlockEntityType(ID), wandHUD)
        );
        map.put(BotaniaFlowerBlocks.MANASTAR, flower -> new ManastarWandHUD((ManastarBlockEntity)flower));
        return Collections.unmodifiableMap(map);
    });
    //endregion

}
