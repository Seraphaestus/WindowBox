package amaryllis.window_box.flower.functional;

import amaryllis.window_box.Client;
import amaryllis.window_box.DataGen;
import amaryllis.window_box.WindowBox;
import amaryllis.window_box.flower.CustomFlower;
import amaryllis.window_box.flower.FlowerHelper;
import com.min01.gravityapi.EntityTags;
import com.min01.gravityapi.api.GravityChangerAPI;
import com.min01.gravityapi.util.GCUtil;
import com.min01.gravityapi.util.RotationUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fml.common.Mod;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.api.block_entity.SpecialFlowerBlockEntity;
import vazkii.botania.client.render.block_entity.FloatingFlowerBlockEntityRenderer;
import vazkii.botania.client.render.block_entity.SpecialFlowerBlockEntityRenderer;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.FloatingSpecialFlowerBlock;
import vazkii.botania.common.item.block.SpecialFlowerBlockItem;
import vazkii.botania.common.item.equipment.bauble.ManaseerMonocleItem;
import vazkii.botania.common.lib.BotaniaTags;
import vazkii.botania.xplat.BotaniaConfig;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Supplier;

import static amaryllis.window_box.Registry.*;
import static amaryllis.window_box.flower.CustomFlower.FLOATING;

@Mod.EventBusSubscriber(modid = WindowBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TopsyTulip extends FunctionalFlowerBlockEntity {

    public static final String ID = "topsy_tulip";

    public static final int COST = 5;
    public static final int MIN_COST = 1;
    public static final int RANGE = 4;
    public static final int HEIGHT = 1;


    public static void register() {
        RegisterBlockOnly(ID, () -> new TopsyTulip.Block(ID, () -> (BlockEntityType<TopsyTulip>) getBlockEntityType(ID)));
        RegisterItem(ID, () -> new SpecialFlowerBlockItem(getBlock(ID), new Item.Properties()));
        RegisterBlock(FLOATING(ID), () -> new TopsyTulip.FloatingBlock(BotaniaBlocks.FLOATING_PROPS, () -> (BlockEntityType<TopsyTulip>) getBlockEntityType(ID)));
        FlowerHelper.RegisterPottedFlower(ID);

        CustomFlower.RegisterBlockEntityType(ID, TopsyTulip::new);
        RegisterBlockEntityRenderer(ID, TopsyTulip.FloatingRenderer::new);
        RegisterWandHUD(ID, Client.FUNCTIONAL_FLOWER_HUD);

        CustomFlower.RegisterStewEffect(ID, MobEffects.LEVITATION, 6);

        DataGen.markOptional(ID, FLOATING(ID));
        // NB: Manual blockstate
        DataGen.ItemModel(ID, (models, name) -> DataGen.Models.basicItem(models, name, WindowBox.RL("amaryllis/window_box/block/" + name)));
        DataGen.BlockModel(FLOATING(ID), DataGen.Models::blockManual);
        DataGen.ItemModel(FLOATING(ID), DataGen.Models::blockParent);
        DataGen.TagSpecialFlower(ID, CustomFlower.FlowerType.FUNCTIONAL);
        DataGen.Recipes.FloatingFlower(ID);

        DataGen.TagBlock(ID, BotaniaTags.Blocks.UNWANDABLE);
        DataGen.TagBlock(FLOATING(ID), BotaniaTags.Blocks.UNWANDABLE);
    }

    public TopsyTulip(BlockPos pos, BlockState state) {
        super(getBlockEntityType(ID), pos, state);
    }


    @Override
    public void tickFlower() {
        super.tickFlower();
        if (getMana() < COST || redstoneSignal > 0) return;

        Direction effectDirection = this.getBlockState().getValue(TopsyTulip.Block.FACING).getOpposite();
        AABB area = getArea(getEffectivePos(), effectDirection);

        boolean usedMana = false;
        for (Entity entity: level.getEntitiesOfClass(Entity.class, area, EntityTags::canChangeGravity)) {
            var cost = getCost(entity);
            if (getMana() < cost) continue;

            if (!tryChangeGravity(entity, effectDirection, area)) continue;
            addMana(-cost);
            usedMana = true;
            if (getMana() < MIN_COST) break;
        }
        if (usedMana) sync();
    }

    protected boolean tryChangeGravity(Entity entity, Direction effectDirection, AABB area) {
        if (entity.isSpectator()) return false;

        var component = GravityChangerAPI.getGravityComponent(entity);
        var entityGravityDir = component.getCurrGravityDirection();

        boolean isOpposite = entityGravityDir.equals(effectDirection.getOpposite());
        // Unideal but fixes severe issue when trying to e.g. go from floor to ceiling directly; just have to use a wall in-between
        if (isOpposite) return false;

        Vec3 entityPos = isOpposite ? entity.getEyePosition() : entity.position();
        if (!area.contains(entityPos)) return false;

        Vec3 vEffectDirection = Vec3.atLowerCornerOf(effectDirection.getNormal());
        Vec3 effectCenter = Vec3.atCenterOf(getEffectivePos()).add(vEffectDirection.scale(0.5));
        double ADJUSTMENT = 0.1;
        effectCenter = effectCenter.add(vEffectDirection.scale(-ADJUSTMENT));
        Vec3 posDelta = entityPos.subtract(effectCenter);
        double distanceToPlane = -posDelta.dot(vEffectDirection);
        if (distanceToPlane < -ADJUSTMENT - 0.001) return false;

        Vec3 floorDelta = vEffectDirection.multiply(0.9, 0.9, 0.9);
        Vec3 vFloorPos = entityPos.add(floorDelta);
        BlockPos floorPos = new BlockPos(Mth.floor(vFloorPos.x), Mth.floor(vFloorPos.y), Mth.floor(vFloorPos.z));
        if (!entityWillCollide(entity, floorPos, vEffectDirection)) return false;

        Vec3 local = RotationUtil.vecWorldToPlayer(posDelta, effectDirection);
        double dX = GCUtil.distanceToRange(local.x, -0.5, 0.5);
        double dZ = GCUtil.distanceToRange(local.z, -0.5, 0.5);
        double distance = Math.sqrt(dX * dX + dZ * dZ + distanceToPlane * distanceToPlane);
        double priority = (isOpposite ? 990 : 1000) - distance;
        if (entityGravityDir.equals(effectDirection)) priority += 10;

        component.applyGravityDirectionEffect(effectDirection, null, priority);
        return true;
    }

    protected static boolean entityWillCollide(Entity entity, BlockPos pos, Vec3 effectDirection) {
        var state = entity.level().getBlockState(pos);
        VoxelShape blockShape = state.getCollisionShape(entity.level(), pos, CollisionContext.of(entity));
        for (int i = 1; i <= 9; i++) {
            var delta = effectDirection.multiply(i * 0.1, i * 0.1, i * 0.1);
            VoxelShape blockShapeOffset = blockShape.move(
                    pos.getX() - delta.x,
                    pos.getY() - delta.y,
                    pos.getZ() - delta.z);
            if (Shapes.joinIsNotEmpty(blockShapeOffset, Shapes.create(entity.getBoundingBox()), BooleanOp.AND))
                return true;
        }
        return false;
    }

    protected AABB getArea(BlockPos pos, Direction direction) {
        final double allowance = 1; // Extra distance into the ground for step-up between flowers on different heights
        return switch (direction) {
            case UP -> new AABB(pos.getX() - RANGE, pos.getY() - allowance, pos.getZ() - RANGE,
                                pos.getX() + 1 + RANGE, pos.getY() + HEIGHT, pos.getZ() + 1 + RANGE);

            case DOWN -> new AABB(pos.getX() - RANGE, pos.getY() + 1 + allowance, pos.getZ() - RANGE,
                                  pos.getX() + 1 + RANGE, pos.getY() + 1 - HEIGHT, pos.getZ() + 1 + RANGE);

            case EAST -> new AABB(pos.getX() - allowance, pos.getY() - RANGE, pos.getZ() - RANGE,
                                  pos.getX() + HEIGHT, pos.getY() + 1 + RANGE, pos.getZ() + 1 + RANGE);

            case WEST -> new AABB(pos.getX() + 1 + allowance, pos.getY() - RANGE, pos.getZ() - RANGE,
                                  pos.getX() + 1 - HEIGHT, pos.getY() + 1 + RANGE, pos.getZ() + 1 + RANGE);

            case SOUTH -> new AABB(pos.getX() - RANGE, pos.getY() - RANGE, pos.getZ() - allowance,
                                   pos.getX() + 1 + RANGE, pos.getY() + 1 + RANGE, pos.getZ() + HEIGHT);

            case NORTH -> new AABB(pos.getX() - RANGE, pos.getY() - RANGE, pos.getZ() + 1 + allowance,
                                   pos.getX() + 1 + RANGE, pos.getY() + 1 + RANGE, pos.getZ() + 1 - HEIGHT);
        };
    }

    protected int getCost(Entity entity) {
        if (entity instanceof Player || entity instanceof Mob) return COST;
        return MIN_COST;
    }

    @Override
    public RadiusDescriptor getRadius() {
        return RadiusDescriptor.Rectangle.square(getEffectivePos(), RANGE);
    }

    @Override
    public boolean acceptsRedstone() {
        return true;
    }

    @Override
    public int getColor() {
        return 0xaa44ff;
    }

    @Override
    public int getMaxMana() {
        return 1000;
    }


    protected static class Block extends CustomFlower {
        public static final DirectionProperty FACING = BlockStateProperties.FACING;

        public Block(String ID, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType) {
            super(ID, blockEntityType, properties());
            this.registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
        }

        public static BlockBehaviour.Properties properties() {
            var properties = defaultProperties();
            properties.offsetFunction = Optional.of(TopsyTulip.Block::customOffsetFunction);
            return properties;
        }
        protected static Vec3 customOffsetFunction(BlockState state, BlockGetter blockGetter, BlockPos pos) {
            var direction = state.getValue(FACING);
            long seed = Mth.getSeed(pos.getX(), 0, pos.getZ());
            float f = state.getBlock().getMaxHorizontalOffset();
            double d0 = Mth.clamp(((double)((float)(seed & 15L) / 15.0F) - 0.5D) * 0.5D, -f, f);
            double d1 = Mth.clamp(((double)((float)(seed >> 8 & 15L) / 15.0F) - 0.5D) * 0.5D, -f, f);

            if (direction.getAxis() == Direction.Axis.X) return new Vec3(0, d0, d1);
            if (direction.getAxis() == Direction.Axis.Z) return new Vec3(d0, d1, 0);
            return new Vec3(d0, 0, d1);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(FACING);
        }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getClickedFace());
        }

        @Override
        public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            Direction direction = state.getValue(FACING);
            BlockPos groundPos = pos.relative(direction, -1);
            BlockState groundState = level.getBlockState(groundPos);
            if (state.getBlock() == this) return groundState.canSustainPlant(level, groundPos, direction, this);
            return this.mayPlaceOn(groundState, level, groundPos);
        }
    }

    protected static class FloatingBlock extends FloatingSpecialFlowerBlock {
        public FloatingBlock(Properties props, Supplier<BlockEntityType<? extends SpecialFlowerBlockEntity>> blockEntityType) {
            super(props, blockEntityType);
            this.registerDefaultState(defaultBlockState().setValue(Block.FACING, Direction.UP));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(Block.FACING);
        }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(Block.FACING, context.getClickedFace());
        }
    }

    protected static class FloatingRenderer extends SpecialFlowerBlockEntityRenderer<TopsyTulip> {
        public FloatingRenderer(BlockEntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(SpecialFlowerBlockEntity flower, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            if (flower.isFloating() && !BotaniaConfig.client().staticFloaters()) {
                ms.pushPose();
                rotateToFace(flower, ms);
                FloatingFlowerBlockEntityRenderer.renderFloatingIsland(flower, partialTicks, ms, buffers, overlay);
                ms.popPose();
            }
            if (!(Minecraft.getInstance().cameraEntity instanceof LivingEntity view)) return;
            if (!ManaseerMonocleItem.hasMonocle(view)) return;

            BlockPos pos = null;
            HitResult ray = Minecraft.getInstance().hitResult;
            if (ray != null && ray.getType() == HitResult.Type.BLOCK) {
                pos = ((BlockHitResult) ray).getBlockPos();
            }

            boolean hasBindingAttempt = hasBindingAttempt(view, flower.getBlockPos());
            if (hasBindingAttempt || flower.getBlockPos().equals(pos)) {
                ms.pushPose();
                rotateToFace(flower, ms);
                if (hasBindingAttempt) ms.translate(0, 0.005, 0);
                renderRadius(flower, ms, buffers, flower.getRadius());
                ms.translate(0, 0.002, 0);
                renderRadius(flower, ms, buffers, flower.getSecondaryRadius());
                ms.popPose();
            }
        }

        protected static void rotateToFace(SpecialFlowerBlockEntity flower, PoseStack ms) {
            ms.translate(0.5, 0.5, 0.5);
            ms.mulPose(getFacing(flower).getRotation());
            ms.translate(-0.5, -0.5, -0.5);
        }
        protected static Direction getFacing(SpecialFlowerBlockEntity flower) {
            return flower.getBlockState().getOptionalValue(TopsyTulip.Block.FACING).orElse(Direction.UP);
        }
    }
}
