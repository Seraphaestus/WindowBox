package amaryllis.window_box.tree;

import amaryllis.window_box.Registry;
import amaryllis.window_box.Scheduling;
import amaryllis.window_box.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector2i;

import static amaryllis.window_box.Registry.ClientOnly.RegisterParticleType;
import static amaryllis.window_box.Registry.ClientOnly.getParticleType;
import static net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn;

public class ChthonicYew {

    public static final String ID = "chthonic_yew";

    public static BlockSetType SET_TYPE;
    public static WoodType WOOD_TYPE;
    public static RegistryObject<SoundEvent> GROW_SOUND;

    public static void register() {
        SET_TYPE = TreeHelper.RegisterWoodSetType(ID);
        WOOD_TYPE = TreeHelper.RegisterWoodType(ID, SET_TYPE);

        unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            RegisterParticleType(ID + "_leaves", LeafParticle::Factory);
        });

        TreeHelper.register(ID, SET_TYPE, WOOD_TYPE, ChthonicYew.Sapling::new, ChthonicYew.Leaves::new,
                MapColor.TERRACOTTA_WHITE, MapColor.TERRACOTTA_BLACK, MapColor.TERRACOTTA_WHITE);

        GROW_SOUND = Registry.RegisterSound("grow_" + ID);
    }

    public static class Sapling extends SpecialSapling {
        public Sapling() {
            super(true, 4);
        }

        @Override
        public void growTree(ServerLevel level, BlockPos pos, RandomSource random) {
            Vec3 saplingsCenter = new Vec3(pos.getX() + 1, pos.getY(), pos.getZ() + 1);
            Rotation rotation = rotateTowardsNearestPlayer(level, saplingsCenter);
            Direction rotationPosDir = rotatedTowardsX(rotation) ? Direction.EAST : Direction.SOUTH;

            Vector2i offset;
            Vector2i portalOffset;
            switch (rotation) {
                case CLOCKWISE_90:
                    offset = new Vector2i(5, -5);
                    portalOffset = new Vector2i(1, 0);
                    break;
                case CLOCKWISE_180:
                    offset = new Vector2i(6, 5);
                    portalOffset = new Vector2i(0, 1);
                    break;
                case COUNTERCLOCKWISE_90:
                    offset = new Vector2i(-4, 6);
                    portalOffset = new Vector2i(0, 0);
                    break;
                default:
                    offset = new Vector2i(-5, -4);
                    portalOffset = new Vector2i(0, 0);
                    break;
            }

            if (!placeStructure(ID, level, pos.offset(offset.x, 0, offset.y), rotation, random)) {
                return;
            }

            var portalOrigin = pos.offset(portalOffset.x, 0, portalOffset.y);
            Scheduling.onNextTick(() -> placePortal(level, portalOrigin, rotationPosDir, 2, 3, Blocks.NETHER_PORTAL, NetherPortalBlock.AXIS));

            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    level.playSound(null, pos.offset(x, 0, z), GROW_SOUND.get(), SoundSource.BLOCKS);
                }
            }
        }
    }

    public static class Leaves extends CustomLeaves {
        protected int getParticleRarity() { return 8; }
        protected ParticleOptions fetchParticle() { return getParticleType(ID + "_leaves"); }

        @Override
        protected BlockPos[] getAdjacentPositions() {
            return Util.POSITIONS_1M_CUBE;
        }
    }

}
