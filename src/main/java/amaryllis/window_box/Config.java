package amaryllis.window_box;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class Config {

    protected static class Builder extends ForgeConfigSpec.Builder {
        @Override
        public ForgeConfigSpec.Builder comment(String comment) {
            return super.comment(" " + comment);
        }
        public ForgeConfigSpec.Builder newComment(String comment) {
            return super.comment("").comment(comment);
        }
    }

    //region Common
    private static final Builder BUILDER = new Builder();
    static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SNAPDRESSON_DONT_LOOK_INSIDE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SNAPDRESSON_DONT_COMPARE_TAGS;

    public static final ForgeConfigSpec.BooleanValue GLORY_INCARNATA_CAN_PREVENT_ZOMBIFICATION;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GLORY_INCARNATA_CURABLE;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISPELAGONIUM_BLACKLIST;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WITHER_POTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_MINING_FATIGUE_POTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LEVITATION_POTION;

    static {
        BUILDER.push("Snapdresson");
        BUILDER.comment("Snapdresson can look inside any items with a tag {Items} or {BlockEntityTag.Items}");
        SNAPDRESSON_DONT_LOOK_INSIDE = BUILDER
                .comment("A list of item IDs (like the trinket case or shulker box) to force the Snapdresson not to look inside, if their data is inconsistent with the vanilla format")
                .defineListAllowEmpty("snapdresson_prevent_looking_inside", List.of(), Config::validItem);
        SNAPDRESSON_DONT_COMPARE_TAGS = BUILDER
                .comment("A list of top level item NBT-tag keys which should be ignored when comparing two items to see if one matches the other")
                .defineListAllowEmpty("snapdresson_ignore_tags_for_comparison", List.of("Damage", "RepairCost", "mana", "baubleUUID", "soulbindUUID"), Config::validString);
        BUILDER.pop();

        BUILDER.push("Groundberry");
        BUILDER.comment("The Groundberry will automatically apply to any BrushableBlock, and bury loot from window_box/loot_tables/groundberry/block_id.json");
        BUILDER.pop();

        BUILDER.push("Oxidaisel");
        BUILDER.comment("The Oxidaisel will automatically apply to any WeatheringCopper");
        BUILDER.pop();

        BUILDER.push("Glory Incarnata");
        GLORY_INCARNATA_CAN_PREVENT_ZOMBIFICATION = BUILDER
                .comment("Whether or not the Glory Incarnata prevents Piglins and Hoglins* from zombifying")
                .comment("*(or any mobs which extend those classes)")
                .define("glory_incarnata_can_prevent", true);
        GLORY_INCARNATA_CURABLE = BUILDER
                .newComment("What mobs the Glory Incarnata will cure, and the mobs they are cured into")
                .comment("The list should be a sets of 3: original mob ID, new mob ID, time to cure in ticks")
                .defineListAllowEmpty("glory_incarnata_can_cure", List.of(
                        "minecraft:zombie_villager", "minecraft:villager", "200",
                        "minecraft:zombified_piglin", "minecraft:piglin", "200",
                        "minecraft:zoglin", "minecraft:hoglin", "200"
                ), Config::validString);
        BUILDER.pop();

        BUILDER.push("Witch's Pupil");
        /*WITCH_PUPIL_CAN_ONLY_LINK_TO_PARENT = BUILDER
                .comment("If true, Eye Berries only link to the same instance of the flower they were plucked from")
                .comment("If false, Eye Berries link to the block pos they were plucked from, even if the flower has been broken and replaced")
                .define("witch_pupil_can_only_link_to_parent", false);*/
        BUILDER.pop();

        BUILDER.push("Dispelagonium");
        DISPELAGONIUM_BLACKLIST = BUILDER
                .comment("A list of (potion) effect IDs which the Dispelagonium cannot dispel")
                .defineListAllowEmpty("dispelagonium_blacklist", List.of(), Config::validEffect);
        BUILDER.newComment("The following potions are also configurably added in order to facilitate the Dispelagonium:");
        ENABLE_WITHER_POTION = BUILDER.define("enable_wither_potion", true);
        ENABLE_MINING_FATIGUE_POTION = BUILDER.define("enable_mining_fatigue_potion", true);
        ENABLE_LEVITATION_POTION = BUILDER.define("enable_levitation_potion", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
    //endregion



    protected static boolean validString(final Object obj) {
        return obj instanceof String;
    }
    protected static boolean validItem(final Object obj) {
        return (obj instanceof final String name) && ForgeRegistries.ITEMS.containsKey(ResourceLocation.parse(name));
    }
    protected static boolean validEffect(final Object obj) {
        return (obj instanceof final String name) && ForgeRegistries.MOB_EFFECTS.containsKey(ResourceLocation.parse(name));
    }
}
