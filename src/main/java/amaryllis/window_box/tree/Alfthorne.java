package amaryllis.window_box.tree;

import amaryllis.window_box.Registry;
import amaryllis.window_box.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import vazkii.botania.api.state.BotaniaStateProperties;
import vazkii.botania.api.state.enums.AlfheimPortalState;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.common.advancements.AlfheimPortalTrigger;
import vazkii.botania.common.block.BotaniaBlock;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.block_entity.AlfheimPortalBlockEntity;
import vazkii.botania.common.block.block_entity.BotaniaBlockEntity;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;
import vazkii.botania.common.block.mana.ManaPoolBlock;
import vazkii.botania.common.item.WandOfTheForestItem;
import vazkii.botania.xplat.BotaniaConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static vazkii.botania.common.block.PylonBlock.Variant.NATURA;
import static vazkii.botania.common.block.block_entity.AlfheimPortalBlockEntity.MANA_COST_OPENING;
import static vazkii.botania.common.block.block_entity.AlfheimPortalBlockEntity.MIN_REQUIRED_PYLONS;

public class Alfthorne {

    public static final String ID = "alfthorne";

    public static BlockSetType SET_TYPE;
    public static WoodType WOOD_TYPE;
    public static RegistryObject<SoundEvent> GROW_SOUND;

    public static void register() {
        SET_TYPE = TreeHelper.RegisterWoodSetType(ID);
        WOOD_TYPE = TreeHelper.RegisterWoodType(ID, SET_TYPE);

        TreeHelper.register(ID, SET_TYPE, WOOD_TYPE, Alfthorne.Sapling::new, Alfthorne.Leaves::new,
                MapColor.COLOR_GREEN, MapColor.TERRACOTTA_BROWN, MapColor.COLOR_GREEN);

        Registry.RegisterBlockEntityType(ID + "_sapling", SaplingBlockEntity::new);

        GROW_SOUND = Registry.RegisterSound("grow_" + ID);
    }

    public static class Sapling extends SpecialSapling implements EntityBlock {
        public Sapling() {
            super(false, 24);
        }

        @Override
        public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean isClient) {
            return false;
        }

        @Override
        public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {}

        @Override
        public void growTree(ServerLevel level, BlockPos pos, RandomSource random) {
            if (!saplingsHaveRoom(level, pos)) return;

            Rotation rotation = rotateTowardsNearestPlayer(level, pos.getCenter()).getRotated(Rotation.COUNTERCLOCKWISE_90);
            var portalAxis = rotatedTowardsX(rotation) ? AlfheimPortalState.ON_X : AlfheimPortalState.ON_Z;

            Vector2i offset;
            switch (rotation) {
                case CLOCKWISE_90:
                    offset = new Vector2i(10, -4);
                    break;
                case CLOCKWISE_180:
                    offset = new Vector2i(4, 10);
                    break;
                case COUNTERCLOCKWISE_90:
                    offset = new Vector2i(-10, 4);
                    break;
                default:
                    offset = new Vector2i(-4, -10);
                    break;
            }

            if (!placeStructure(ID, level, pos.offset(offset.x, 0, offset.y), rotation, random)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                return;
            }

            var gatewayPos = pos.above();
            if (level.getBlockEntity(gatewayPos) instanceof AlfheimPortalBlockEntity gateway) {
                var state = level.getBlockState(gatewayPos);
                level.setBlockAndUpdate(gatewayPos, state.setValue(BotaniaStateProperties.ALFPORTAL_STATE, portalAxis));

                var nbt = gateway.serializeNBT();
                nbt.putInt("ticksOpen", 61); // 61 = 1 tick after the AlfheimPortalBlockEntity is fully opened and stable
                gateway.deserializeNBT(nbt);
            }

            level.playSound(null, pos, GROW_SOUND.get(), SoundSource.BLOCKS);
        }

        // For some reason making the BE implement Wandable didn't work, so...
        @Override
        public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
            var stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WandOfTheForestItem &&
                level.getBlockEntity(pos) instanceof Alfthorne.SaplingBlockEntity sapling &&
                !sapling.isActivating() && sapling.locatePylons(true).size() >= MIN_REQUIRED_PYLONS)
            {
                sapling.ticksActivating = 0;
                if (player instanceof ServerPlayer serverPlayer) {
                    AlfheimPortalTrigger.INSTANCE.trigger(serverPlayer, serverPlayer.serverLevel(), pos, stack);
                }
                return InteractionResult.SUCCESS;
            }
            return super.use(state, level, pos, player, hand, hitResult);
        }

        @Override
        public @NotNull BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
            return new SaplingBlockEntity(pos, state);
        }

        @Override @SuppressWarnings("unchecked")
        public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return BotaniaBlock.createTickerHelper(type, SaplingBlockEntity.getBEType(), SaplingBlockEntity::commonTick);
        }
    }

    public static class SaplingBlockEntity extends BotaniaBlockEntity {
        protected int ticksActivating = -1;
        protected final List<BlockPos> cachedPylonPositions = new ArrayList<>();
        public static final int PYLON_RANGE = 5;

        // Take a little longer than AlfheimPortalBlockEntity's times (which are 2.5s/3s) for UX
        public static final int TIME_OF_COST_PAID = 4 * Util.SECONDS;
        public static final int GROW_TIME = 5 * Util.SECONDS;

        public static BlockEntityType getBEType() {
            return Registry.getBlockEntityType(ID + "_sapling");
        }

        public SaplingBlockEntity(BlockPos pos, BlockState state) {
            super(getBEType(), pos, state);
        }

        public static void commonTick(Level level, BlockPos pos, BlockState state, SaplingBlockEntity self) {
            if (!self.isActivating()) return;

            // Start animation immediately (instead of only after TIME_OF_COST_PAID) for better-feeling UX
            List<BlockPos> pylons = self.locatePylons(self.ticksActivating == TIME_OF_COST_PAID);
            if (pylons.size() < MIN_REQUIRED_PYLONS) {
                self.stopActivating();
                return;
            }

            for (BlockPos pylon: pylons) self.lightPylon(pylon);
            if (self.ticksActivating == TIME_OF_COST_PAID) self.consumeMana(pylons, MANA_COST_OPENING);
            self.ticksActivating++;

            if (self.ticksActivating >= GROW_TIME && level instanceof ServerLevel serverLevel) {
                ((Alfthorne.Sapling)state.getBlock()).growTree(serverLevel, pos, level.random);
            }
        }

        public boolean isActivating() { return ticksActivating >= 0; }
        public void stopActivating() { ticksActivating = -1; }

        protected void lightPylon(BlockPos pos) {
            // Simulate pylon activated particles
            Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.75 + (Math.random() - 0.5 * 0.25), pos.getZ() + 0.5);

            if (BotaniaConfig.client().elfPortalParticlesEnabled()) {
                double time = level.getGameTime();
                time += new Random(worldPosition.hashCode()).nextInt(1000);
                time /= 5;

                double r = 0.75 + Math.random() * 0.05;
                double x = worldPosition.getX() + 0.5 + Math.cos(time) * r;
                double z = worldPosition.getZ() + 0.5 + Math.sin(time) * r;

                Vec3 ourCoords = new Vec3(x, worldPosition.getY() + 0.25, z);
                center = center.subtract(0, 0.5, 0);
                Vec3 movementVector = center.subtract(ourCoords).normalize().scale(0.2);

                WispParticleData data = WispParticleData.wisp(0.25F + (float) Math.random() * 0.1F, (float) Math.random() * 0.25F, 0.75F + (float) Math.random() * 0.25F, (float) Math.random() * 0.25F, 1);
                level.addParticle(data, x, worldPosition.getY() + 0.25, z, 0, -(-0.075F - (float) Math.random() * 0.015F), 0);
                if (level.random.nextInt(3) == 0) {
                    WispParticleData data1 = WispParticleData.wisp(0.25F + (float) Math.random() * 0.1F, (float) Math.random() * 0.25F, 0.75F + (float) Math.random() * 0.25F, (float) Math.random() * 0.25F);
                    level.addParticle(data1, x, worldPosition.getY() + 0.25, z, (float) movementVector.x, (float) movementVector.y, (float) movementVector.z);
                }
            }

            if (level.random.nextBoolean() && level.isClientSide) {
                SparkleParticleData data = SparkleParticleData.sparkle((float) Math.random(), NATURA.r, NATURA.g, NATURA.b, 2);
                level.addParticle(data, worldPosition.getX() + Math.random(), worldPosition.getY() + Math.random() * 1.5, worldPosition.getZ() + Math.random(), 0, 0, 0);
            }
        }

        public void consumeMana(List<BlockPos> pylons, int totalCost) {
            int costPerPool = Math.max(1, totalCost / pylons.size());
            totalCost = costPerPool * pylons.size();

            List<ManaPoolBlockEntity> pools = new ArrayList<>();
            int manaConsumed = 0;
            for (BlockPos pos: pylons) {
                lightPylon(pos);

                var blockEntity = level.getBlockEntity(pos.below());
                if (blockEntity instanceof ManaPoolBlockEntity pool) {
                    if (pool.getCurrentMana() < costPerPool) {
                        stopActivating();
                        return;
                    }
                    if (!level.isClientSide) {
                        pools.add(pool);
                        manaConsumed += costPerPool;
                    }
                }
            }

            if (manaConsumed >= totalCost) {
                for (ManaPoolBlockEntity pool: pools) {
                    pool.receiveMana(-costPerPool);
                    pool.craftingEffect(false);
                }
            }
        }


        public List<BlockPos> locatePylons(boolean forceRescan) {
            if (!forceRescan && cachedPylonPositions.size() >= MIN_REQUIRED_PYLONS) {
                List<BlockPos> cachedResult = new ArrayList<>();
                for (BlockPos pos: cachedPylonPositions) {
                    if (hasValidPylon(pos)) cachedResult.add(pos);
                }
                if (cachedResult.size() >= MIN_REQUIRED_PYLONS) return cachedResult;
            }

            List<BlockPos> result = new ArrayList<>();
            for (BlockPos pos: BlockPos.betweenClosed(
                    getBlockPos().offset(-PYLON_RANGE, -PYLON_RANGE, -PYLON_RANGE),
                    getBlockPos().offset(PYLON_RANGE, PYLON_RANGE, PYLON_RANGE))) {
                if (hasValidPylon(pos)) result.add(pos.immutable());
            }

            cachedPylonPositions.clear();
            cachedPylonPositions.addAll(result);
            return result;
        }

        protected boolean hasValidPylon(BlockPos pos) {
            return getLevel().hasChunkAt(pos)
                    && getLevel().getBlockState(pos).is(BotaniaBlocks.naturaPylon)
                    && getLevel().getBlockState(pos.below()).getBlock() instanceof ManaPoolBlock;
        }


        @Override
        public void writePacketNBT(CompoundTag cmp) {
            cmp.putInt("ticksActivating", ticksActivating);
        }

        @Override
        public void readPacketNBT(CompoundTag cmp) {
            ticksActivating = cmp.getInt("ticksActivating");
        }
    }

    public static class Leaves extends CustomLeaves {
        @Override
        protected BlockPos[] getAdjacentPositions() {
            return Util.POSITIONS_1M_CUBE;
        }
    }

}
