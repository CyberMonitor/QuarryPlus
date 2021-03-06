package com.yogpc.qp.test;

import com.yogpc.qp.QuarryPlus;
import com.yogpc.qp.utils.Holder;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import org.junit.jupiter.api.Test;
import scala.jdk.javaapi.CollectionConverters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemAccessTest {
    @Test
    void exist() {
        Item item = Holder.itemStatusChecker();
        assertEquals(new ResourceLocation(QuarryPlus.modID, QuarryPlus.Names.statuschecker), item.getRegistryName());
    }

    @Test
    void myItemsInForgeRegistry() {
        IForgeRegistry<Block> blocks = ForgeRegistries.BLOCKS;
        assertTrue(CollectionConverters.asJava(Holder.blocks())
            .stream().allMatch(blocks::containsValue), "All blocks are in forge registry.");
        IForgeRegistry<Item> items = ForgeRegistries.ITEMS;
        assertTrue(CollectionConverters.asJava(Holder.items()).stream().allMatch(items::containsValue), "All items are in registry.");
    }
}
