package com.finchy.pipeorgans.content.console;

import com.finchy.pipeorgans.network.AllPackets;
import com.finchy.pipeorgans.network.packet.PedalConfigPacket;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class PedalEditScreen extends AbstractSimiContainerScreen<PedalEditMenu> {

    private static final int COLOR_PANEL        = 0xFF313131;
    private static final int COLOR_PANEL_BORDER = 0xFF161616;
    private static final int COLOR_SLOT_BORDER  = 0xFF373737;
    private static final int COLOR_SLOT_INNER   = 0xFF8B8B8B;
    private static final int COLOR_LABEL        = 0xFFB0B0B0;
    private static final int COLOR_LEVEL_BG     = 0xFF3A3A3A;

    private final BlockPos consolePos;
    private final int pedalIndex;
    private final PedalData.PedalType pedalType;

    private EditBox nameBox;

    public PedalEditScreen(PedalEditMenu container, Inventory inv, Component title) {
        super(container, inv, title);
        this.consolePos = container.getConsoleBE().getBlockPos();
        this.pedalIndex = container.getPedalIndex();
        this.pedalType = container.pedalType;
    }

    @Override
    protected void init() {
        setWindowSize(PedalEditMenu.guiWidth(), PedalEditMenu.guiHeight(pedalType, menu.levelCount));
        setWindowOffset(0, 0);
        super.init();

        PedalData.Pedal pedal = menu.getConsoleBE().getPedalData().getPedal(pedalIndex);

        // Name field
        nameBox = new EditBox(font,
                leftPos + PedalEditMenu.FIELD_X,
                topPos + PedalEditMenu.NAME_BOX_Y,
                PedalEditMenu.FIELD_W, PedalEditMenu.BOX_H,
                Component.empty());
        nameBox.setMaxLength(32);
        nameBox.setValue(pedal.name);
        addRenderableWidget(nameBox);

        // For crescendo: add/remove level buttons
        if (pedalType == PedalData.PedalType.CRESCENDO) {
            int addRemoveY = topPos + PedalEditMenu.CONTENT_TOP + menu.levelCount * PedalEditMenu.ROW_H + 2;
            addRenderableWidget(Button.builder(Component.literal("+"), b -> addLevel())
                    .bounds(leftPos + PedalEditMenu.SLOT_COL_A, addRemoveY, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal("-"), b -> removeLevel())
                    .bounds(leftPos + PedalEditMenu.SLOT_COL_A + 22, addRemoveY, 20, 18).build());
        }

        // Save button
        int saveY = topPos + menu.buttonsY() + (pedalType == PedalData.PedalType.CRESCENDO ? 22 : 4);
        int bw = 50;
        int startX = leftPos + PedalEditMenu.FIELD_X + (PedalEditMenu.FIELD_W - bw) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.pipeorgans.pedal_edit.save"), b -> sendSave())
                .bounds(startX, saveY, bw, 18).build());
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    private void sendSave() {
        PedalData.Pedal built = menu.buildPedal(nameBox.getValue());
        AllPackets.getChannel().sendToServer(new PedalConfigPacket(consolePos, pedalIndex, built));
        onClose();
    }

    private void addLevel() {
        if (menu.levelCount >= PedalData.MAX_CRESCENDO_LEVELS)
            return;
        // Grow ghost inventory
        int newLevelCount = menu.levelCount + 1;
        net.minecraftforge.items.ItemStackHandler newInv =
                new net.minecraftforge.items.ItemStackHandler(newLevelCount * 2);
        for (int i = 0; i < menu.ghostInventory.getSlots(); i++)
            newInv.setStackInSlot(i, menu.ghostInventory.getStackInSlot(i));
        menu.ghostInventory = newInv;
        menu.levelCount = newLevelCount;
        rebuildScreen();
    }

    private void removeLevel() {
        if (menu.levelCount == 0)
            return;
        int newLevelCount = menu.levelCount - 1;
        net.minecraftforge.items.ItemStackHandler newInv =
                new net.minecraftforge.items.ItemStackHandler(Math.max(2, newLevelCount * 2));
        for (int i = 0; i < newLevelCount * 2; i++)
            newInv.setStackInSlot(i, menu.ghostInventory.getStackInSlot(i));
        menu.ghostInventory = newInv;
        menu.levelCount = newLevelCount;
        rebuildScreen();
    }

    private void rebuildScreen() {
        // Rebuild slots and widgets to reflect new level count
        minecraft.setScreen(null);
        PedalEditMenu newMenu = PedalEditMenu.create(
                menu.containerId, minecraft.player.getInventory(),
                menu.getConsoleBE(), pedalIndex);
        // Copy level count and ghost inventory from current menu into new menu
        newMenu.levelCount = menu.levelCount;
        newMenu.ghostInventory = menu.ghostInventory;
        minecraft.setScreen(new PedalEditScreen(newMenu, minecraft.player.getInventory(), title));
    }

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox.isFocused()) {
            if (keyCode == 256) { sendSave(); return true; }
            nameBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == 256 || minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            sendSave();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nameBox.isFocused())
            return nameBox.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        // Don't auto-save on close — only on explicit Save button
        super.onClose();
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, COLOR_PANEL_BORDER);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COLOR_PANEL);

        // Filter rows
        int rows = pedalType == PedalData.PedalType.ENCLOSED ? 1 : menu.levelCount;
        for (int i = 0; i < rows; i++) {
            int rowY = PedalEditMenu.filterRowY(i);
            if (pedalType == PedalData.PedalType.CRESCENDO) {
                // Level number background
                graphics.fill(leftPos + PedalEditMenu.SLOT_COL_A - 2, topPos + rowY,
                        leftPos + PedalEditMenu.SLOT_COL_B + 20, topPos + rowY + PedalEditMenu.ROW_H - 2, COLOR_LEVEL_BG);
            }
            drawSlotBackground(graphics, PedalEditMenu.SLOT_COL_A + 1, rowY + 1);
            drawSlotBackground(graphics, PedalEditMenu.SLOT_COL_B + 1, rowY + 1);
        }

        // Player inventory slots
        int invX = PedalEditMenu.playerInvX();
        int invY = PedalEditMenu.playerInvY(pedalType, menu.levelCount);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlotBackground(graphics, invX + col * 18, invY + row * 18);
        for (int col = 0; col < 9; col++)
            drawSlotBackground(graphics, invX + col * 18, invY + 58);
    }

    private void drawSlotBackground(GuiGraphics graphics, int guiX, int guiY) {
        int x = leftPos + guiX;
        int y = topPos + guiY;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, COLOR_SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, COLOR_SLOT_INNER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, PedalEditMenu.FIELD_X, PedalEditMenu.TITLE_Y, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.pipeorgans.pedal_edit.name"),
                PedalEditMenu.FIELD_X, PedalEditMenu.NAME_LABEL_Y, COLOR_LABEL, false);

        int rows = pedalType == PedalData.PedalType.ENCLOSED ? 1 : menu.levelCount;
        for (int i = 0; i < rows; i++) {
            int rowY = PedalEditMenu.filterRowY(i) + 5;
            if (pedalType == PedalData.PedalType.CRESCENDO) {
                graphics.drawString(font,
                        Component.literal(String.valueOf(i + 1)),
                        PedalEditMenu.SLOT_COL_B + 22, rowY, COLOR_LABEL, false);
            }
        }

        String freqLabel = pedalType == PedalData.PedalType.ENCLOSED
                ? "gui.pipeorgans.pedal_edit.frequency_pair"
                : "gui.pipeorgans.pedal_edit.levels";
        graphics.drawString(font, Component.translatable(freqLabel),
                PedalEditMenu.SLOT_COL_A, PedalEditMenu.CONTENT_TOP - 10, COLOR_LABEL, false);
    }
}
