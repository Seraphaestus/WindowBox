package amaryllis.window_box.flower.functional;

import amaryllis.window_box.Config;
import amaryllis.window_box.DataGen;
import amaryllis.window_box.Registry;
import amaryllis.window_box.WindowBox;
import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.flower.FlowerHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.api.block_entity.SpecialFlowerBlockEntity;
import vazkii.botania.client.core.handler.ClientTickHandler;
import vazkii.botania.client.core.helper.RenderHelper;
import vazkii.botania.client.render.block_entity.SpecialFlowerBlockEntityRenderer;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.FloatingSpecialFlowerBlock;
import vazkii.botania.common.helper.VecHelper;
import vazkii.botania.common.item.block.SpecialFlowerBlockItem;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.Registry.ClientOnly.RegisterBlockEntityRenderer;
import static amaryllis.window_box.flower.CustomFlower.FLOATING;
import static net.minecraft.world.level.block.Block.box;
import static net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn;

public class Snapdresson extends SpecialFlowerBlockEntity implements Nameable {

    public static final String ID = "snapdresson";
    public static final VoxelShape SHAPE = box(4, 0, 4, 12, 16, 12);

    public static RegistryObject<SoundEvent> SAVE_SOUND;
    public static RegistryObject<SoundEvent> LOAD_SOUND;

    public static final String TAG_LOADOUT = "memory";
    protected SortedMap<String, ItemStack[]> loadout = null;

    public static Map<Item, Boolean> DONT_LOOK_INSIDE = new HashMap<>();
    public static Map<String, Boolean> DONT_COMPARE_TAGS = new HashMap<>();

    public static final float NAMETAG_OFFSET = 0.8f;

    protected @Nullable Component name;

    public static void register() {
        RegisterBlockOnly(ID, () -> new Snapdresson.Block(ID, () -> (BlockEntityType<Snapdresson>) getBlockEntityType(ID),
            CustomFlower.noOffset()).setShape(SHAPE));
        RegisterItem(ID, () -> new SpecialFlowerBlockItem(getBlock(ID), new Item.Properties()));
        RegisterBlock(FLOATING(ID), () -> new Snapdresson.FloatingBlock(BotaniaBlocks.FLOATING_PROPS, () -> (BlockEntityType<Snapdresson>) getBlockEntityType(ID)));
        FlowerHelper.RegisterPottedFlower(ID);

        CustomFlower.RegisterBlockEntityType(ID, Snapdresson::new);
        unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            RegisterBlockEntityRenderer(ID, SpecialRenderer::new);
            RegisterWandHUD(ID, flower -> new Snapdresson.WandHUD((Snapdresson) flower));
        });

        CustomFlower.RegisterStewEffect(ID, MobEffects.GLOWING, 8);

        DataGen.SpecialFlower(ID, CustomFlower.FlowerType.MISC);
        // Custom loot table for retaining CustomName
        Registry.MANUAL_BLOCK_LOOT.add(ID);
        Registry.MANUAL_BLOCK_LOOT.add(FLOATING(ID));

        SAVE_SOUND = Registry.RegisterSound(ID + "_save");
        LOAD_SOUND = Registry.RegisterSound(ID + "_load");
    }

    public Snapdresson(BlockPos pos, BlockState state) {
        super(getBlockEntityType(ID), pos, state);
    }

    public static void onConfigFirstLoaded() {
        DONT_LOOK_INSIDE.clear();
        for (String ID: Config.SNAPDRESSON_DONT_LOOK_INSIDE.get()) {
            var loc = ResourceLocation.parse(ID);
            if (!ForgeRegistries.ITEMS.containsKey(loc)) continue;
            DONT_LOOK_INSIDE.put(ForgeRegistries.ITEMS.getValue(loc), true);
        }

        DONT_COMPARE_TAGS.clear();
        for (String ID: Config.SNAPDRESSON_DONT_COMPARE_TAGS.get()) {
            DONT_COMPARE_TAGS.put(ID, true);
        }
    }

    public InteractionResult interact(Player player, ItemStack heldItem, Level level, BlockPos pos) {
        if (heldItem.is(Items.NAME_TAG) && heldItem.hasCustomHoverName()) {
            setCustomName(heldItem.getHoverName());
            setChanged();
            sync();
            return InteractionResult.SUCCESS;
        }

        var curio = CuriosApi.getCurio(heldItem).orElse(null);
        if (curio != null) {
            if (trySaveItemToLoadout(heldItem, curio, player)) {
                setChanged();
                sync();
                level.playSound(null, pos, SAVE_SOUND.get(), SoundSource.BLOCKS);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.CONSUME;
        }

        if (hasLoadout()) {
            if (loadLoadout(player)) {
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5, 0, 0.05, 0);
                level.playSound(null, pos, LOAD_SOUND.get(), SoundSource.BLOCKS);
            }
            return InteractionResult.SUCCESS;
        }

        if (canSaveLoadout(player)) {
            saveLoadout(player);
            level.playSound(null, pos, SAVE_SOUND.get(), SoundSource.BLOCKS);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    public boolean hasLoadout() {
        return loadout != null;
    }

    public boolean canSaveLoadout(Player player) {
        var curiosInventory = CuriosApi.getCuriosInventory(player);
        if (!curiosInventory.isPresent()) return false;
        var curios = curiosInventory.orElse(null).getCurios();
        for (String slotType: curios.keySet()) {
            var slots = curios.get(slotType).getStacks();
            for (int i = 0; i < slots.getSlots(); i++) {
                if (!slots.getStackInSlot(i).isEmpty()) return true;
            }
        }
        return false;
    }

    public void clearLoadout() {
        loadout = null;
        setChanged();
        sync();
    }
    public void saveLoadout(Player player) {
        loadout = null;
        CuriosApi.getCuriosInventory(player).ifPresent(curiosInventory -> {
            loadout = new TreeMap<>(Snapdresson::SlotComparator);
            curiosInventory.getCurios().forEach((slotType, slots) -> {
                var stacks = new ItemStack[slots.getSlots()];
                for (int i = 0; i < stacks.length; i++) {
                    stacks[i] = slots.getStacks().getStackInSlot(i).copy();
                }
                loadout.put(slotType, stacks);
            });
        });
        setChanged();
        sync();
    }
    public boolean trySaveItemToLoadout(ItemStack stack, ICurio curio, Player player) {
        var curiosInventory = CuriosApi.getCuriosInventory(player).orElse(null);
        if (curiosInventory == null) return false;

        boolean matchesAnySlot = false;
        // First pass -> try place curio in the first empty slot
        for (var slotEntry: curiosInventory.getCurios().entrySet()) {
            String slotType = slotEntry.getKey();
            ICurioStacksHandler slots = slotEntry.getValue();
            var renderStates = slots.getRenders();

            for (int i = 0; i < slots.getSlots(); i++) {
                var slotContext = new SlotContext(slotType, player, i, false, renderStates.size() > i && renderStates.get(i));
                if (isCuriosValidForSlot(stack, curio, slotContext)) {
                    matchesAnySlot = true;
                    if (loadout != null && !loadout.get(slotType)[i].isEmpty()) continue;
                    if (loadout == null) initializeEmptyLoadout(curiosInventory);
                    loadout.get(slotType)[i] = stack.copy();
                    return true;
                }
            }
        }

        if (loadout != null && matchesAnySlot) {
            // Second pass -> try replace curio in valid slots
            for (var slotEntry : curiosInventory.getCurios().entrySet()) {
                String slotType = slotEntry.getKey();
                ICurioStacksHandler slots = slotEntry.getValue();
                var renderStates = slots.getRenders();
                var slotContext = new SlotContext(slotType, player, 0, false, renderStates.size() > 0 && renderStates.get(0));
                if (isCuriosValidForSlot(stack, curio, slotContext)) {
                    initializeEmptyLoadoutSlot(slotType, slots);
                    loadout.get(slotType)[0] = stack.copy();
                    return true;
                }
            }
        }
        return false;
    }
    protected boolean isCuriosValidForSlot(ItemStack stack, ICurio curio, SlotContext slotContext) {
        return CuriosApi.isStackValid(slotContext, stack) && curio.canEquip(slotContext) && curio.canEquipFromUse(slotContext);
    }
    protected void initializeEmptyLoadout(ICuriosItemHandler curiosInventory) {
        loadout = new TreeMap<>(Snapdresson::SlotComparator);
        curiosInventory.getCurios().forEach(this::initializeEmptyLoadoutSlot);
    }
    protected void initializeEmptyLoadoutSlot(String slotType, ICurioStacksHandler slots) {
        var stacks = new ItemStack[slots.getSlots()];
        Arrays.fill(stacks, ItemStack.EMPTY);
        loadout.put(slotType, stacks);
    }

    public boolean loadLoadout(Player player) {
        if (!CuriosApi.getCuriosInventory(player).isPresent()) return false;
        var curiosInventory = CuriosApi.getCuriosInventory(player).orElse(null);
        boolean loadedAny = false;
        var curios = curiosInventory.getCurios();
        for (String slotType: loadout.keySet()) {
            var stacks = loadout.get(slotType);
            for (int i = 0; i < stacks.length; i++) {
                if (loadLoadoutSlot(player, curios, slotType, i, stacks[i])) loadedAny = true;
            }
        }
        return loadedAny;
    }
    protected boolean loadLoadoutSlot(Player player, Map<String, ICurioStacksHandler> curios, String slotType, int slotIndex, ItemStack desired) {
        var curiosStacks = curios.get(slotType).getStacks();
        var wornCurio = curiosStacks.getStackInSlot(slotIndex);
        if (desired.isEmpty() || matchesDesiredStack(wornCurio, desired)) return false;

        // Check other curios slots
        for (String otherSlotType: curios.keySet()) {
            var otherStacks = curios.get(otherSlotType).getStacks();
            for (int otherIndex = 0; otherIndex < otherStacks.getSlots(); otherIndex++) {
                if (slotType.equals(otherSlotType) && otherIndex == slotIndex) continue; // Only look at the *other* slots

                var otherCurio = otherStacks.getStackInSlot(otherIndex);
                if (matchesDesiredStack(otherCurio, desired)) {
                    // Swap stacks
                    otherStacks.setStackInSlot(otherIndex, wornCurio);
                    curiosStacks.setStackInSlot(slotIndex, otherCurio);
                    return true;
                }
            }
        }

        // Check player inventory
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (matchesDesiredStack(stack, desired)) {
                // Swap stacks
                inventory.setItem(i, wornCurio);
                curiosStacks.setStackInSlot(slotIndex, stack);
                return true;
            }
            // Sub Inventories
            if (stack.hasTag() && !DONT_LOOK_INSIDE.containsKey(stack.getItem())) {
                var tag = stack.getTag();
                if (tag.contains("Items") &&
                    loadLoadoutSlotFromTag(tag.getList("Items", Tag.TAG_COMPOUND), desired, curiosStacks, slotIndex, wornCurio))
                        return true;
                if (tag.contains("BlockEntityTag") && tag.getCompound("BlockEntityTag").contains("Items") &&
                    loadLoadoutSlotFromTag(tag.getCompound("BlockEntityTag").getList("Items", Tag.TAG_COMPOUND), desired, curiosStacks, slotIndex, wornCurio))
                        return true;
            }
        }
        return false;
    }
    protected boolean loadLoadoutSlotFromTag(ListTag items, ItemStack desired, IDynamicStackHandler curiosStacks, int slotIndex, ItemStack wornCurio) {
        for (int ii = 0; ii < items.size(); ii++) {
            var subItem = ItemStack.of(items.getCompound(ii));
            if (matchesDesiredStack(subItem, desired)) {
                // Swap stacks
                items.set(ii, wornCurio.serializeNBT());
                curiosStacks.setStackInSlot(slotIndex, subItem);
                return true;
            }
        }
        return false;
    }
    protected boolean matchesDesiredStack(ItemStack stack, ItemStack desired) {
        if (stack.isEmpty()) return false;
        if (!stack.is(desired.getItem())) return false;
        if (!stack.hasTag() || !desired.hasTag()) return true; // Match if same item and either has blank data

        var stackData = stack.getTag();
        var desiredData = desired.getTag();
        for (String key: stackData.getAllKeys()) {
            if (desiredData.contains(key) &&
                !desiredData.get(key).equals(stackData.get(key)) &&
                !DONT_COMPARE_TAGS.containsKey(key))
                    return false;
        }
        return true;
    }

    protected static int SlotComparator(String slotTypeA, String slotTypeB) {
        var slotA = CuriosApi.getSlot(slotTypeA).orElse(null);
        var slotB = CuriosApi.getSlot(slotTypeB).orElse(null);
        if (slotA == null || slotB == null) return slotTypeA.compareTo(slotTypeB);
        return slotA.getOrder() - slotB.getOrder();
    }

    public List<ItemStack> getLoadoutItemsList() {
        var list = new ArrayList<ItemStack>();
        if (loadout != null) {
            for (String slotType: loadout.keySet()) {
                list.addAll(Arrays.asList(loadout.get(slotType)));
            }
        }
        return list;
    }

    @Override
    public RadiusDescriptor getRadius() {
        return new RadiusDescriptor.Circle(getEffectivePos(), 0);
    }

    public int getColor() {
        return 0xff8866;
    }

    //region Nameable
    public void setCustomName(Component name) {
        this.name = name;
    }

    public @NotNull Component getName() {
        return this.name != null ? this.name : Component.empty();
    }

    public @NotNull Component getDisplayName() {
        return getName();
    }

    public @Nullable Component getCustomName() {
        return name;
    }
    //endregion

    @Override
    public void writeToPacketNBT(CompoundTag tag) {
        super.writeToPacketNBT(tag);

        if (loadout != null) {
            var tag_loadout = new CompoundTag();
            for (String slotType: loadout.keySet()) {
                var tag_stacks = new ListTag();
                for (ItemStack stack: loadout.get(slotType)) tag_stacks.add(stack.serializeNBT());
                tag_loadout.put(slotType, tag_stacks);
            }
            tag.put(TAG_LOADOUT, tag_loadout);
        }

        if (this.name != null) {
            tag.putString("CustomName", Component.Serializer.toJson(name));
        }
    }

    @Override
    public void readFromPacketNBT(CompoundTag tag) {
        super.readFromPacketNBT(tag);

        if (tag.contains(TAG_LOADOUT)) {
            if (loadout == null) loadout = new TreeMap<>(Snapdresson::SlotComparator);
            else loadout.clear();

            WindowBox.LOGGER.info("Reading loadout from nbt");
            var tag_loadout = tag.getCompound(TAG_LOADOUT);
            for (String slotType: tag_loadout.getAllKeys()) {
                var tag_stacks = tag_loadout.getList(slotType, Tag.TAG_COMPOUND);
                var stacks = new ItemStack[tag_stacks.size()];
                for (int i = 0; i < stacks.length; i++) {
                    stacks[i] = ItemStack.of(tag_stacks.getCompound(i));
                }
                loadout.put(slotType, stacks);
            }
            WindowBox.LOGGER.info("Loaded loadout: {} -> {}", tag_loadout, loadout);
        } else {
            loadout = null;
        }

        if (tag.contains("CustomName", Tag.TAG_STRING)) {
            name = Component.Serializer.fromJson(tag.getString("CustomName"));
        }
    }


    protected static class Block extends CustomFlower {
        public Block(String ID, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType, BlockBehaviour.Properties properties) {
            super(ID, blockEntityType, properties);
        }

        @Override
        public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
            Snapdresson snapdresson = (Snapdresson) level.getBlockEntity(pos);
            if (snapdresson == null) return InteractionResult.FAIL;
            return snapdresson.interact(player, player.getItemInHand(hand), level, pos);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity source, ItemStack stack) {
            if (!stack.hasCustomHoverName()) return;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof Snapdresson snapdresson) {
                snapdresson.setCustomName(stack.getHoverName());
            }
        }
    }

    protected static class FloatingBlock extends FloatingSpecialFlowerBlock {
        public FloatingBlock(Properties properties, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType) {
            super(properties, blockEntityType);
        }

        @Override
        public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
            Snapdresson snapdresson = (Snapdresson) level.getBlockEntity(pos);
            if (snapdresson == null) return InteractionResult.FAIL;
            return snapdresson.interact(player, player.getItemInHand(hand), level, pos);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity source, ItemStack stack) {
            if (!stack.hasCustomHoverName()) return;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof Snapdresson snapdresson) {
                snapdresson.setCustomName(stack.getHoverName());
            }
        }
    }

    public static class WandHUD implements vazkii.botania.api.block.WandHUD {
        protected final Snapdresson flower;

        public WandHUD(Snapdresson flower) {
            this.flower = flower;
        }

        @Override
        public void renderHUD(GuiGraphics gui, Minecraft mc) {
            if (!flower.hasLoadout()) return;

            String name = I18n.get(flower.getBlockState().getBlock().getDescriptionId());
            int color = flower.getColor();

            int y = mc.getWindow().getGuiScaledHeight() / 2 + 8;
            int centerX = mc.getWindow().getGuiScaledWidth() / 2;
            int width = (Math.max(102, mc.font.width(name)) + 4);
            int height = 32;
            RenderHelper.renderHUDBox(gui, centerX - width / 2, y, centerX + width / 2, y + height);

            int titleX = centerX - mc.font.width(name) / 2;
            y += 2;
            gui.drawString(mc.font, name, titleX, y, color);

            var items = flower.getLoadoutItemsList();
            y += 11;
            for (int i = 0; i < items.size(); i++) {
                int x = centerX - width / 2 + i * width / items.size();
                gui.renderItem(items.get(i), x, y);
            }
        }
    }

    public static class SpecialRenderer extends SpecialFlowerBlockEntityRenderer<Snapdresson> {
        public SpecialRenderer(BlockEntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(SpecialFlowerBlockEntity flower, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            super.render(flower, partialTicks, ms, buffers, light, overlay);

            Snapdresson snapdresson = (Snapdresson) flower;
            if (snapdresson.hasLoadout()) {
                ms.pushPose();

                var items = snapdresson.getLoadoutItemsList();
                float[] angles = new float[items.size()];

                float anglePer = 360f / items.size();
                float totalAngle = 0;
                for (int i = 0; i < angles.length; i++) angles[i] = totalAngle += anglePer;

                double time = ClientTickHandler.ticksInGame + partialTicks;
                var itemRenderer = Minecraft.getInstance().getItemRenderer();

                final float ITEM_SCALE = 0.5f;
                final float ITEM_RADIUS = 0.4f;
                final float ITEM_OSCILLATION = 0.05f;
                for (int index = 0; index < items.size(); index++) {
                    if (items.get(index).isEmpty()) continue;

                    ms.pushPose();
                    ms.translate(0.5, 0.25 + 0.1 * (6 - index), 0.5); // Translate to block center + y offset
                    ms.mulPose(VecHelper.rotateY(angles[index] + (float) time)); // Rotate the items around the center
                    ms.translate(ITEM_RADIUS, 0, 0);
                    ms.mulPose(VecHelper.rotateY(90F)); // Rotate the items to face out
                    ms.translate(0, ITEM_OSCILLATION * Math.sin((time + index * 10) / 5), 0); // Float up and down

                    ms.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
                    itemRenderer.renderStatic(items.get(index), ItemDisplayContext.GROUND, light, overlay, ms, buffers, flower.getLevel(), 0);
                    ms.popPose();
                }
                ;

                ms.popPose();
            }

            if (snapdresson.name != null) {
                renderNameTag(snapdresson.level, snapdresson.getEffectivePos().getCenter(), snapdresson.name, ms, buffers, light);
            }
        }

        protected static void renderNameTag(Level level, Vec3 pos, Component text, PoseStack ms, MultiBufferSource buffers, int light) {
            var mc = Minecraft.getInstance();
            var camera = mc.gameRenderer.getMainCamera();

            boolean inRenderDistance = camera.getPosition().distanceToSqr(pos) <= 64f; // 8m
            if (!inRenderDistance) return;

            HitResult hitResult = level.clip(new ClipContext(camera.getPosition(), pos.add(0, NAMETAG_OFFSET, 0), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, null));
            if (hitResult.getType() != HitResult.Type.MISS) return;

            Font font = mc.font;
            float bgOpacity = mc.options.getBackgroundOpacity(0.25f);
            int bgColor = (int)(bgOpacity * 255) << 24;

            ms.pushPose();
            ms.translate(0.5, 0.5 + NAMETAG_OFFSET, 0.5);
            ms.mulPose(camera.rotation());
            ms.scale(-0.025f, -0.025f, 0.025f);
            Matrix4f mat = ms.last().pose();
            float xOffset = (float)(-font.width(text) / 2);
            font.drawInBatch(text, xOffset, 0, 0x20ffffff, false, mat, buffers, Font.DisplayMode.SEE_THROUGH, bgColor, light);
            font.drawInBatch(text, xOffset, 0, -1, false, mat, buffers, Font.DisplayMode.NORMAL, 0, light);
            ms.popPose();
        }
    }
}
