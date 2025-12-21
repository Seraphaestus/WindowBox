package amaryllis.window_box;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.LazyOptional;
import top.theillusivec4.curios.api.CuriosApi;
import vazkii.botania.api.block_entity.SpecialFlowerBlockEntity;
import vazkii.botania.client.fx.WispParticleData;

import java.util.*;

public class Util {

    public static final int ONE_SECOND = 20;
    public static final int SECONDS = ONE_SECOND;
    public static final float PIXEL = 1 / 16f;

    //region Position Sets
    // Ordered by closest adjacency
    public static final BlockPos[] POSITIONS_1M_CUBE = {
            BP(0, 0,-1), BP(-1, 0, 0), BP(1, 0,0), BP(0, 0,1),
            BP(0, -1, 0), BP(0, 1, 0),

            BP(0, -1,-1),  BP(-1, -1, 0),  BP(1, -1,0), BP(0, -1,1),
            BP(-1, 0, -1), BP(1, 0,-1), BP(-1, 0, 1),  BP(1, 0,1),
            BP(0, 1,-1), BP(-1, 1, 0),  BP(1, 1,0), BP(0, 1,1),

            BP(-1, -1, -1), BP(1, -1,-1), BP(-1, -1, 1), BP(1, -1,1),
            BP(-1, 1, -1), BP(1, 1,-1), BP(-1, 1, 1), BP(1, 1,1),
    };

    public static final BlockPos[] POSITIONS_2M_SQUARE = {
            BP(-2, -2), BP(-1, -2), BP(0, -2), BP(1, -2), BP(2, -2),
            BP(-2, -1), BP(-1, -1), BP(0, -1), BP(1, -1), BP(2, -1),
            BP(-2,  0), BP(-1,  0), BP(0,  0), BP(1,  0), BP(2,  0),
            BP(-2,  1), BP(-1,  1), BP(0,  1), BP(1,  1), BP(2,  1),
            BP(-2,  2), BP(-1,  2), BP(0,  2), BP(1,  2), BP(2,  2),
    };

    public static final BlockPos[] POSITIONS_2M_DIAMOND = {
            // d = 1m
            BP(-1, 0), BP(0, -1), BP(1, 0), BP(0, 1),
            // d = 2m
            BP(-2, 0), BP(-1, -1), BP(0, -2), BP(1, -1),
            BP(2, 0), BP(1, 1), BP(0, 2), BP(-1, 1)
    };

    protected static BlockPos BP(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }
    protected static BlockPos BP(int x, int z) {
        return new BlockPos(x, 0, z);
    }
    //endregion


    public static <T> T PickRandom(T[] array, RandomSource random) {
        return array[random.nextInt(array.length)];
    }
    public static <T> T PickRandom(List<T> list, RandomSource random) {
        return list.get(random.nextInt(list.size()));
    }


    public static Property<?> getBlockProperty(BlockState state, String name) {
        return state.getProperties().stream()
                .filter(property -> property.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
    public static BlockState trySetBooleanBlockProperty(BlockState state, String name, boolean value) {
        var property = getBlockProperty(state, name);
        if (property != null && state.getValue(property).equals(!value)) {
            return state.cycle(property);
        }
        return state;
    }

    public static Vec3 getFlowerCenter(Level level, BlockPos pos) {
        var flowerOffset = level.getBlockState(pos).getOffset(level, pos);
        return pos.getCenter().add(flowerOffset.x, -0.5, flowerOffset.z);
    }

    public static void doActiveWispParticles(SpecialFlowerBlockEntity blockEntity, int color) {
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8 & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        doWispParticles(blockEntity, r, g, b, 0.5f, 0.9f);
    }
    public static void doNegativeWispParticles(SpecialFlowerBlockEntity blockEntity) {
        doWispParticles(blockEntity, 0.1F, 0.1F, 0.1F, 1f, 0.5f);
    }
    private static void doWispParticles(SpecialFlowerBlockEntity blockEntity, float r, float g, float b, float speed, float yOffset) {
        WispParticleData data = WispParticleData.wisp((float)Math.random() / 6, r, g, b, 1);
        blockEntity.emitParticle(data,
                0.5 + Math.random() * 0.2 - 0.1,
                yOffset + Math.random() * 0.2 - 0.1,
                0.5 + Math.random() * 0.2 - 0.1,
                0, speed * (float)Math.random() / 30, 0);
    }


    public static void playLocalSound(ServerPlayer player, SoundEvent sound, SoundSource source) {
        playLocalSound(player, sound, source, 1, 1);
    }
    public static void playLocalSound(ServerPlayer player, SoundEvent sound, SoundSource source, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(Holder.direct(sound), source,
                player.getX(), player.getY(), player.getZ(), volume, pitch, player.level().getRandom().nextLong()));
    }


    //region Data Tags
    public static CompoundTag GetOrCreateData(CompoundTag persistentData, String groupID) {
        if (!persistentData.contains(groupID)) persistentData.put(groupID, new CompoundTag());
        return persistentData.getCompound(groupID);
    }
    public static boolean HasData(CompoundTag persistentData, String groupID, String dataID) {
        return persistentData.contains(groupID) && persistentData.getCompound(groupID).contains(dataID);
    }
    // Returns false if the int data has been decremented below 1 and deleted
    public static boolean ModifyData_PositiveInt(CompoundTag persistentData, String groupID, String dataID, int delta) {
        if (!persistentData.contains(groupID)) return true;
        var groupData = persistentData.getCompound(groupID);
        if (!groupData.contains(dataID)) return true;
        return ModifyData_PositiveInt(persistentData, groupID, groupData, dataID, delta);
    }
    public static boolean ModifyData_PositiveInt(CompoundTag persistentData, String groupID, CompoundTag groupData, String dataID, int delta) {
        var newValue = groupData.getInt(dataID) + delta;
        if (newValue > 0) {
            groupData.putInt(dataID, newValue);
            return true;
        } else {
            groupData.remove(dataID);
            if (groupData.isEmpty()) persistentData.remove(groupID);
            return false;
        }
    }
    public static void SetData_Int(CompoundTag persistentData, String groupID, String dataID, int value) {
        if (!persistentData.contains(groupID)) persistentData.put(groupID, new CompoundTag());
        persistentData.getCompound(groupID).putInt(dataID, value);
    }
    public static void PutData_Pos(CompoundTag data, String prefix, Level level, BlockPos pos) {
        data.putString(prefix + "dimension", level.dimension().location().toString());
        data.putInt(prefix + "x", pos.getX());
        data.putInt(prefix + "y", pos.getY());
        data.putInt(prefix + "z", pos.getZ());
    }
    public static ServerLevel GetData_Level(ServerLevel context, CompoundTag data, String prefix) {
        ResourceLocation levelID = ResourceLocation.parse(data.getString(prefix + "dimension"));
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, levelID);
        return context.getServer().getLevel(levelKey);
    }
    public static ChunkPos GetData_ChunkPos(CompoundTag data, String prefix) {
        if (!data.contains(prefix + "x") || !data.contains(prefix + "z")) return null;
        return new ChunkPos(data.getInt(prefix + "x"), data.getInt(prefix + "z"));
    }
    public static BlockPos GetData_Pos(CompoundTag data, String prefix) {
        return new BlockPos(data.getInt(prefix + "x"), data.getInt(prefix + "y"), data.getInt(prefix + "z"));
    }
    public static void PutData_PosD(CompoundTag data, String prefix, Level level, Vec3 pos) {
        data.putString(prefix + "dimension", level.dimension().location().toString());
        data.putDouble(prefix + "x", pos.x);
        data.putDouble(prefix + "y", pos.y);
        data.putDouble(prefix + "z", pos.z);
    }
    public static void PutData_PosD(CompoundTag data, String prefix, Level level, Vec3 pos, float rotY, float rotX) {
        PutData_PosD(data, prefix, level, pos);
        data.putFloat(prefix + "rotY", rotY);
        data.putFloat(prefix + "rotX", rotX);
    }
    public static Vec3 GetData_PosD(CompoundTag data, String prefix) {
        return new Vec3(data.getDouble(prefix + "x"), data.getDouble(prefix + "y"), data.getDouble(prefix + "z"));
    }
    public static void TeleportPlayerFromData(ServerPlayer player, CompoundTag data, String prefix) {
        if (!data.contains(prefix + "dimension") || !data.contains(prefix + "x")) return;

        var originalLevel = Util.GetData_Level(player.serverLevel(), data, prefix);
        var originalPos = Util.GetData_PosD(data, prefix);
        float rotY = data.contains(prefix + "rotY") ? data.getFloat(prefix + "rotY") : player.getYRot();
        float rotX = data.contains(prefix + "rotX") ? data.getFloat(prefix + "rotX") : player.getXRot();
        player.teleportTo(originalLevel, originalPos.x, originalPos.y, originalPos.z, Set.of(), rotY, rotX);
    }

    public static int[] SerializeBlockPositions(List<BlockPos> blockPositions) {
        var output = new int[blockPositions.size() * 3];
        for (int i = 0; i < blockPositions.size(); i++) {
            output[i * 3] = blockPositions.get(i).getX();
            output[i * 3 + 1] = blockPositions.get(i).getY();
            output[i * 3 + 2] = blockPositions.get(i).getZ();
        }
        return output;
    }
    public static void DeserializeBlockPositions(int[] input, List<BlockPos> output) {
        output.clear();
        var n = input.length / 3;
        for (int i = 0; i < n; i++) {
            output.add(new BlockPos(input[i * 3], input[i * 3 + 1], input[i * 3 + 2]));
        }
    }
    //endregion

    public static BlockState convertBlock(BlockState state, Block newType) {
        if (state.getBlock() == newType) return state;
        BlockState output = newType.defaultBlockState();
        for (var property: state.getProperties()) {
            output = trySetBlockStateValue(output, property, state.getValue(property));
        }
        return output;
    }
    protected static <T extends Comparable<T>, V>  BlockState trySetBlockStateValue(BlockState state, Property<T> property, V value) {
        return state.trySetValue(property, (T)value);
    }
}
