package amaryllis.window_box;

import amaryllis.window_box.flower.functional.Dispelagonium;
import amaryllis.window_box.flower.functional.GloryIncarnata;
import amaryllis.window_box.flower.functional.Groundberry;
import amaryllis.window_box.flower.functional.Snapdresson;
import amaryllis.window_box.item.GrassRod;
import amaryllis.window_box.item.Potions;
import amaryllis.window_box.tree.SpecialSapling;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.common.item.BotaniaItems;
import vazkii.botania.common.item.rod.LandsRodItem;
import vazkii.botania.forge.CapabilityUtil;

import static vazkii.botania.common.lib.ResourceLocationHelper.prefix;

@Mod(WindowBox.MOD_ID)
@Mod.EventBusSubscriber(modid = WindowBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class WindowBox {
    public static final String MOD_ID = "window_box";

    public static final Logger LOGGER = LogUtils.getLogger();

    public WindowBox(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        Registry.init(modEventBus);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static ResourceLocation RL(String name) {
        return ResourceLocation.fromNamespaceAndPath(WindowBox.MOD_ID, name);
    }
    public static ResourceLocation RL(String mod, String name) {
        return ResourceLocation.fromNamespaceAndPath(mod, name);
    }
    public static ResourceLocation extendRL(ResourceLocation loc, String suffix) {
        return ResourceLocation.fromNamespaceAndPath(loc.getNamespace(), loc.getPath() + suffix);
    }
    public static ResourceLocation prefixRL(ResourceLocation loc, String prefix) {
        return ResourceLocation.fromNamespaceAndPath(loc.getNamespace(), prefix + loc.getPath());
    }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        NetworkHandler.register();
        event.enqueueWork(Registry::onCommonSetup);
    }

    @SubscribeEvent
    public static void onConfigFirstLoaded(ModConfigEvent.Loading event) {
        if (event.getConfig().getType() == ModConfig.Type.COMMON) {
            Snapdresson.onConfigFirstLoaded();
            GloryIncarnata.onConfigFirstLoaded();
            Dispelagonium.onConfigFirstLoaded();
        }
    }
}
