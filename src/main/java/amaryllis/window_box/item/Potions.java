package amaryllis.window_box.item;

import amaryllis.window_box.Config;
import amaryllis.window_box.Registry;
import amaryllis.window_box.Util;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraftforge.registries.RegistryObject;

import static net.minecraft.world.item.alchemy.Potions.AWKWARD;

public class Potions {

    public static RegistryObject<Potion> WITHER;
    public static RegistryObject<Potion> WITHER_LONG;

    public static RegistryObject<Potion> MINING_FATIGUE;
    public static RegistryObject<Potion> MINING_FATIGUE_LONG;

    public static RegistryObject<Potion> LEVITATION;
    public static RegistryObject<Potion> LEVITATION_LONG;
    public static RegistryObject<Potion> LEVITATION_STRONG;

    public static void register() {
        WITHER = register("wither", MobEffects.WITHER, 15);
        WITHER_LONG = registerLong("wither", MobEffects.WITHER, 30);

        MINING_FATIGUE = register("mining_fatigue", MobEffects.DIG_SLOWDOWN, 60);
        MINING_FATIGUE_LONG = registerLong("mining_fatigue", MobEffects.DIG_SLOWDOWN, 120);

        LEVITATION = register("levitation", MobEffects.LEVITATION, 15);
        LEVITATION_LONG = registerLong("levitation", MobEffects.LEVITATION, 30);
        LEVITATION_STRONG = registerStrong("levitation", MobEffects.LEVITATION, 15, 2);
    }

    public static void registerRecipes() {
        if (Config.ENABLE_WITHER_POTION.get()) {
            PotionBrewing.addMix(AWKWARD, Items.WITHER_SKELETON_SKULL, WITHER.get());
            PotionBrewing.addMix(WITHER.get(), Items.REDSTONE, WITHER_LONG.get());
        }
        if (Config.ENABLE_MINING_FATIGUE_POTION.get()) {
            PotionBrewing.addMix(AWKWARD, Items.DARK_PRISMARINE, MINING_FATIGUE.get());
            PotionBrewing.addMix(MINING_FATIGUE.get(), Items.REDSTONE, MINING_FATIGUE_LONG.get());
        }
        if (Config.ENABLE_LEVITATION_POTION.get()) {
            PotionBrewing.addMix(AWKWARD, Items.SHULKER_SHELL, LEVITATION.get());
            PotionBrewing.addMix(LEVITATION.get(), Items.REDSTONE, LEVITATION_LONG.get());
            PotionBrewing.addMix(LEVITATION.get(), Items.GLOWSTONE_DUST, LEVITATION_STRONG.get());
        }
    }

    protected static RegistryObject<Potion> register(String name, MobEffect effect, int seconds) {
        return Registry.POTIONS.register(name, () -> new Potion(new MobEffectInstance(effect, seconds * Util.SECONDS)));
    }
    protected static RegistryObject<Potion> registerLong(String name, MobEffect effect, int seconds) {
        return Registry.POTIONS.register("long_" + name, () -> new Potion(name, new MobEffectInstance(effect, seconds * Util.SECONDS)));
    }
    protected static RegistryObject<Potion> registerStrong(String name, MobEffect effect, int seconds, int amplifier) {
        return Registry.POTIONS.register("strong_" + name, () -> new Potion(name, new MobEffectInstance(effect, seconds * Util.SECONDS, amplifier)));
    }

}
