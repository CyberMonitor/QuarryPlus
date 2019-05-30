package com.yogpc.qp.machines.advquarry;

import java.util.Optional;
import java.util.function.Consumer;

import com.yogpc.qp.Config;
import com.yogpc.qp.QuarryPlus;
import com.yogpc.qp.compat.BuildcraftHelper;
import com.yogpc.qp.machines.base.IEnchantableTile;
import com.yogpc.qp.machines.base.QPBlock;
import com.yogpc.qp.machines.item.YSetterInteractionObject;
import com.yogpc.qp.machines.quarry.ItemBlockEnchantable;
import com.yogpc.qp.utils.Holder;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.fluid.IFluidState;
import net.minecraft.init.Items;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

import static net.minecraft.state.properties.BlockStateProperties.FACING;

public class BlockAdvQuarry extends QPBlock {

    public BlockAdvQuarry() {
        super(Properties.create(Material.IRON)
            .hardnessAndResistance(1.5f, 10f)
            .sound(SoundType.STONE), QuarryPlus.Names.advquarry, ItemBlockEnchantable::new);
        setDefaultState(getStateContainer().getBaseState().with(FACING, EnumFacing.NORTH).with(QPBlock.WORKING(), false));
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos,
                                   EntityPlayer player, boolean willHarvest, IFluidState fluid) {
        return willHarvest || super.removedByPlayer(state, world, pos, player, willHarvest, fluid);
    }

    @Override
    public void harvestBlock(World worldIn, EntityPlayer player, BlockPos pos, IBlockState state, @Nullable TileEntity te, ItemStack stack) {
        super.harvestBlock(worldIn, player, pos, state, te, stack);
        worldIn.removeBlock(pos);
    }

    @Override
    public void getDrops(IBlockState state, NonNullList<ItemStack> drops, World world, BlockPos pos, int fortune) {
        TileEntity entity = world.getTileEntity(pos);
        if (entity instanceof TileAdvQuarry) {
            TileAdvQuarry quarry = (TileAdvQuarry) entity;
            ItemStack stack = new ItemStack(itemBlock(), 1);
            IEnchantableTile.Util.enchantmentToIS(quarry, stack);
            drops.add(stack);
        }
    }

    @Override
    public boolean onBlockActivated(IBlockState state, World worldIn, BlockPos pos, EntityPlayer playerIn,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(state, worldIn, pos, playerIn, hand, facing, hitX, hitY, hitZ)) return true;
        ItemStack stack = playerIn.getHeldItem(hand);
        if (BuildcraftHelper.isWrench(playerIn, hand, stack, new RayTraceResult(new Vec3d(hitX, hitY, hitZ), facing, pos))) {
            if (!worldIn.isRemote) {
                TileAdvQuarry quarry = (TileAdvQuarry) worldIn.getTileEntity(pos);
                if (quarry != null) {
                    if (stack.getItem() == Items.STICK) {
                        if (Config.common().noEnergy().get())
                            quarry.stickActivated(playerIn);
                        quarry.startFillMode();
                    } else {
                        quarry.G_ReInit();
                        if (Config.common().noEnergy().get()) {
                            quarry.stickActivated(playerIn);
                        }
                    }
                }
            }
            return true;
        } else if (stack.getItem() == Holder.itemStatusChecker()) {
            if (!worldIn.isRemote)
                Optional.ofNullable((IEnchantableTile) worldIn.getTileEntity(pos)).ifPresent(t ->
                    t.sendEnchantMassage(playerIn));
            return true;
        } else if (stack.getItem() == Holder.itemLiquidSelector()) {
            // Not implemented.
            return true;
        } else if (stack.getItem() == Holder.itemYSetter()) {
            if (!worldIn.isRemote)
                NetworkHooks.openGui(((EntityPlayerMP) playerIn), YSetterInteractionObject.apply((TileAdvQuarry) worldIn.getTileEntity(pos)), pos);
            return true;
        } else if (!playerIn.isSneaking()) {
            if (!worldIn.isRemote)
                NetworkHooks.openGui(((EntityPlayerMP) playerIn), (TileAdvQuarry) worldIn.getTileEntity(pos), pos);
            return true;
        }
        return false;
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        if (!worldIn.isRemote) {
            EnumFacing facing = placer.getHorizontalFacing().getOpposite();
            worldIn.setBlockState(pos, state.with(FACING, facing), 2);
            Consumer<TileAdvQuarry> consumer = IEnchantableTile.Util.initConsumer(stack);
            Optional.ofNullable((TileAdvQuarry) worldIn.getTileEntity(pos)).ifPresent(consumer.andThen(TileAdvQuarry.requestTicket));
        }
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, IBlockState> builder) {
        builder.add(FACING, QPBlock.WORKING());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos);
        if (!worldIn.isRemote)
            Optional.ofNullable((TileAdvQuarry) worldIn.getTileEntity(pos)).ifPresent(TileAdvQuarry::energyConfigure);
    }

    @Override
    public void fillItemGroup(ItemGroup group, NonNullList<ItemStack> items) {
        if (!Config.common().disabled().apply(TileAdvQuarry.SYMBOL()).get()) {
            super.fillItemGroup(group, items);
        }
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(IBlockReader worldIn) {
        return Holder.advQuarryType().create();
    }
}