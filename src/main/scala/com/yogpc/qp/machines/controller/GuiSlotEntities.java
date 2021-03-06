package com.yogpc.qp.machines.controller;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.list.ExtendedList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GuiSlotEntities extends ExtendedList<GuiSlotEntities.Entry> {

    private final GuiController parent;

    public GuiSlotEntities(Minecraft mcIn, int widthIn, int heightIn, int topIn, int bottomIn, int slotHeightIn, GuiController parent) {
        super(mcIn, widthIn, heightIn, topIn, bottomIn, slotHeightIn);
        this.parent = parent;
        this.refreshList();
    }

    public void refreshList() {
        this.clearEntries();
        parent.buildModList(this::addEntry, Entry::new);
    }

    public class Entry extends ExtendedList.AbstractListEntry<Entry> {
        public final ResourceLocation location;

        public Entry(ResourceLocation location) {
            this.location = location;
        }

        @Override
        public void render(int entryIdx, int top, int left, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean p_render_8_, float partialTicks) {
            String name = location.toString();
            Minecraft minecraft = Minecraft.getInstance();

            assert minecraft.currentScreen != null;
            minecraft.fontRenderer.drawStringWithShadow(name,
                (minecraft.currentScreen.width - minecraft.fontRenderer.getStringWidth(name)) >> 1,
                top + 2, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double p_mouseClicked_1_, double p_mouseClicked_3_, int p_mouseClicked_5_) {
            GuiSlotEntities.this.setSelected(this);
            return false;
        }
    }
}
