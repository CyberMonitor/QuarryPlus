package com.yogpc.qp.machines.workbench;

import java.util.Optional;

import com.yogpc.qp.QuarryPlus;
import com.yogpc.qp.compat.InvUtils;
import com.yogpc.qp.machines.base.QPBlock;
import com.yogpc.qp.machines.item.ItemQuarryDebug;
import com.yogpc.qp.utils.Holder;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

import static jp.t2v.lab.syntax.MapStreamSyntax.optCast;

public class BlockWorkbench extends QPBlock {
    public BlockWorkbench() {
        super(Properties.create(Material.ANVIL).hardnessAndResistance(3.0f), QuarryPlus.Names.workbench, BlockItem::new);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (InvUtils.isDebugItem(player, hand)) return false;
        if (player.getHeldItem(hand).getItem() == Holder.itemStatusChecker()) {
            if (!worldIn.isRemote) {
                Optional.ofNullable(worldIn.getTileEntity(pos))
                    .flatMap(optCast(TileWorkbench.class))
                    .ifPresent(t -> player.sendStatusMessage(ItemQuarryDebug.energyToString(t), false));
            }
            return true;
        }
        if (!player.isSneaking()) {
            if (!worldIn.isRemote) {
                Optional.ofNullable(worldIn.getTileEntity(pos))
                    .flatMap(optCast(TileWorkbench.class))
                    .ifPresent(t -> NetworkHooks.openGui(((ServerPlayerEntity) player), t, pos));
            }
            return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onReplaced(BlockState state, World worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            if (!worldIn.isRemote) {
                TileEntity entity = worldIn.getTileEntity(pos);
                if (entity instanceof TileWorkbench) {
                    TileWorkbench inventory = (TileWorkbench) entity;
                    for (ItemStack itemstack : inventory.inventory) {
                        InventoryHelper.spawnItemStack(worldIn, pos.getX(), pos.getY(), pos.getZ(), itemstack);
                    }
                    worldIn.updateComparatorOutputLevel(pos, state.getBlock());
                }
            }
        }
        super.onReplaced(state, worldIn, pos, newState, isMoving);
    }

    @Override
    public TileEntity createNewTileEntity(IBlockReader worldIn) {
        return Holder.workbenchTileType().create();
    }
}
